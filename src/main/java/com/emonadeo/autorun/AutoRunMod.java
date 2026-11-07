package com.emonadeo.autorun;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

public class AutoRunMod implements ClientModInitializer {

	public static final String MODID = "autorun";
	public static final File CFG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(),
			"autorun.properties");

	public static boolean configAutoJump = true;
	public static boolean configPersistAutoRun = false;
	public static boolean configShowMessage = true;
	public static boolean configSprint = false;
	public static boolean configStopOnBlock = false;
	public static String configStopBlocks = "";
	public static boolean configChangeDirection = false;

	public static boolean overrideForward = false;
	public static boolean overrideBackward = false;
	public static boolean overrideLeft = false;
	public static boolean overrideRight = false;
	public static boolean overrideSprint = false;

	private static boolean originalAutoJumpSetting = false;

	/**
	 * To avoid immediate deactivation after activation, discard the initial
	 * direction inputs until they are released.
	 * 
	 * When true, ignore directional deactivation inputs
	 */
	private static boolean activating = false;
	private static BlockPos lastDirectionBlockPos;

	private static KeyMapping toggleAutoRunKey;

	@Override
	public void onInitializeClient() {
		loadConfig(CFG_FILE);
		// Re-save so that new properties will appear in old config files
		saveConfig(CFG_FILE);

		toggleAutoRunKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.autorun.toggle",
				InputConstants.Type.KEYBOARD,
				InputConstants.KEY_V, // Default to 'v'
				KeyMapping.Category.MOVEMENT));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleAutoRunKey.consumeClick()) {
				if (client.level == null) {
					continue;
				}
				if (isAutoRunActive()) {
					deactivateAutoRun(client);
				} else {
					activateAutoRun(client);
					activating = true;
				}
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(AutoRunMod::updateDirectionFromBlock);

		// Deactivate when pressing the same or opposite auto-running direction
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (configStopOnBlock && isAutoRunActive() && isOnConfiguredStopBlock(client)) {
				deactivateAutoRun(client);
				return;
			}

			if (activating) {
				if ((overrideForward && !client.options.keyUp.isDown())
						|| (overrideBackward && !client.options.keyDown.isDown())
						|| (overrideLeft && !client.options.keyLeft.isDown())
						|| (overrideRight && !client.options.keyRight.isDown())) {
					activating = false;
				}
				return;
			}

			if ((overrideForward || overrideBackward)
					&& (client.options.keyUp.isDown() || client.options.keyDown.isDown())) {
				deactivateAutoRun(client);
			}
			if ((overrideLeft || overrideRight)
					&& (client.options.keyLeft.isDown() || client.options.keyRight.isDown())) {
				deactivateAutoRun(client);
			}
		});

		ClientEntityEvents.ENTITY_UNLOAD.register((entity, clientWorld) -> {
			if (entity instanceof LocalPlayer) {
				lastDirectionBlockPos = null;
				if (!configPersistAutoRun && isAutoRunActive()) {
					deactivateAutoRun(Minecraft.getInstance());
				}
			}
		});
	}

	private static void updateDirectionFromBlock(Minecraft client) {
		if (client.player == null || client.level == null) {
			lastDirectionBlockPos = null;
			return;
		}

		BlockPos blockPos = client.player.getOnPos();
		if (blockPos.equals(lastDirectionBlockPos)) {
			return;
		}
		lastDirectionBlockPos = blockPos.immutable();

		if (!configChangeDirection || !isAutoRunActive()) {
			return;
		}

		BlockState blockState = client.level.getBlockState(blockPos);
		if (BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString()
				.equals("minecraft:magenta_glazed_terracotta")) {
		float yaw = blockState.getValue(HorizontalDirectionalBlock.FACING).getOpposite().toYRot();
			client.player.setYRot(yaw);
			client.player.setYHeadRot(yaw);
		}
	}

	private static boolean isOnConfiguredStopBlock(Minecraft client) {
		if (client.player == null || client.level == null || configStopBlocks.isBlank()) {
			return false;
		}

		String blockId = BuiltInRegistries.BLOCK
				.getKey(client.level.getBlockState(client.player.getOnPos()).getBlock())
				.toString();
		for (String configuredBlock : configStopBlocks.split(",")) {
			if (blockId.equalsIgnoreCase(configuredBlock.trim())) {
				return true;
			}
		}
		return false;
	}

	private static boolean isAutoRunActive() {
		return overrideForward || overrideBackward || overrideLeft || overrideRight;
	}

	private static void activateAutoRun(Minecraft client) {
		if (configShowMessage) {
			client.player.sendOverlayMessage(
					Component.translatable("commands." + AutoRunMod.MODID + ".enable"));
		}

		if (configAutoJump) {
			originalAutoJumpSetting = client.options.autoJump().get();
			client.options.autoJump().set(true);
			client.options.broadcastOptions();
		}

		if (client.player.isSprinting() || configSprint) {
			overrideSprint = true;
		}
		Input input = client.player.input.keyPresses;
		if (!input.forward() && !input.backward() && !input.left() && !input.right()) {
			// Auto-Run forward if no movement key is pressed
			overrideForward = true;
			return;
		}
		// At least 1 movement key is pressed
		if (input.forward()) {
			overrideForward = true;
		}
		if (input.backward()) {
			overrideBackward = true;
		}
		if (input.left()) {
			overrideLeft = true;
		}
		if (input.right()) {
			overrideRight = true;
		}
	}

	private static void deactivateAutoRun(Minecraft client) {
		if (configShowMessage) {
			client.player.sendOverlayMessage(Component.translatable("commands." + AutoRunMod.MODID + ".disable"));
		}

		overrideForward = false;
		overrideBackward = false;
		overrideLeft = false;
		overrideRight = false;
		overrideSprint = false;

		// Restore Auto-Jump
		if (configAutoJump) {
			client.options.autoJump().set(originalAutoJumpSetting);
			client.options.broadcastOptions();
		}
	}

	public static void loadConfig(File file) {
		try {
			Properties cfg = new Properties();
			if (!file.exists()) {
				saveConfig(file);
			}
			cfg.load(new FileInputStream(file));
			configSprint = Boolean.parseBoolean(cfg.getProperty("alwaysSprint", "false"));
			configPersistAutoRun = Boolean.parseBoolean(cfg.getProperty("persistAutoRun", "false"));
			configShowMessage = Boolean.parseBoolean(cfg.getProperty("showMessage", "true"));
			configAutoJump = Boolean.parseBoolean(cfg.getProperty("autoJump", "true"));
			configStopOnBlock = Boolean.parseBoolean(cfg.getProperty("stopOnBlock", "false"));
			configStopBlocks = cfg.getProperty("stopBlocks", "");
			configChangeDirection = Boolean.parseBoolean(cfg.getProperty("changeDirection", "false"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void saveConfig(File file) {
		try {
			FileOutputStream fos = new FileOutputStream(file, false);
			fos.write(("alwaysSprint=" + configSprint + "\n").getBytes());
			fos.write(("persistAutoRun=" + configPersistAutoRun + "\n").getBytes());
			fos.write(("showMessage=" + configShowMessage + "\n").getBytes());
			fos.write(("autoJump=" + configAutoJump + "\n").getBytes());
			fos.write(("stopOnBlock=" + configStopOnBlock + "\n").getBytes());
			fos.write(("stopBlocks=" + configStopBlocks.replace("\r", "").replace("\n", "") + "\n").getBytes());
			fos.write(("changeDirection=" + configChangeDirection + "\n").getBytes());
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
