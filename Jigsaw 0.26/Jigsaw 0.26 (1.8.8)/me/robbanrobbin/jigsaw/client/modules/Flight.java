package me.robbanrobbin.jigsaw.client.modules;

import org.lwjgl.input.Keyboard;

import me.robbanrobbin.jigsaw.client.WaitTimer;
import me.robbanrobbin.jigsaw.client.events.PreMotionEvent;
import me.robbanrobbin.jigsaw.client.events.UpdateEvent;
import me.robbanrobbin.jigsaw.client.main.Jigsaw;
import me.robbanrobbin.jigsaw.client.module.state.Category;
import me.robbanrobbin.jigsaw.client.settings.ClientSettings;
import me.robbanrobbin.jigsaw.gui.custom.clickgui.CheckBtnSetting;
import me.robbanrobbin.jigsaw.gui.custom.clickgui.ModSetting;
import me.robbanrobbin.jigsaw.gui.custom.clickgui.SliderSetting;
import me.robbanrobbin.jigsaw.gui.custom.clickgui.ValueFormat;
import me.robbanrobbin.jigsaw.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovementInput;

public class Flight extends Module {

	int wait = 6;
	double MACvelY = 0.02;
	double startingHeight;
	double fallSpeed = 0.05;
	double maxY;
	boolean damaging = false;
	private WaitTimer timer = new WaitTimer();
	public double flyHeight;
	private boolean aac;
	private double aad;
	boolean Up = false;
	boolean Start = false;
	private WaitTimer cubeTimer = new WaitTimer();
	
	boolean simulateFall = false;

	public Flight() {
		super("Flight", Keyboard.KEY_F, Category.MOVEMENT, "Allows you to fly.");
	}

	@Override
	public void onDisable() {
		mc.thePlayer.capabilities.isFlying = false;
		super.onDisable();
	}

	public void onPreMotion(PreMotionEvent event) {
		if (this.currentMode.equals("AAC") || currentMode.equals("AAC2")) {
			mc.thePlayer.setSprinting(false);
			if ((mc.thePlayer.fallDistance >= 4.0F) && (!this.aac)) {
				this.aac = true;
				this.aad = (mc.thePlayer.posY + 3.0D);
				mc.thePlayer.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX,
						mc.thePlayer.posY, mc.thePlayer.posZ, true));
			}
			mc.thePlayer.capabilities.isFlying = false;
			if (this.aac) {
				if (mc.thePlayer.onGround) {
					this.aac = false;
				}
				if (mc.thePlayer.posY < this.aad) {
					mc.thePlayer.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX,
							mc.thePlayer.posY, mc.thePlayer.posZ, true));
					if (mc.gameSettings.keyBindSneak.pressed) {
						this.aad -= 2.0D;
					} else if ((mc.gameSettings.keyBindSneak.pressed) && (mc.thePlayer.posY < this.aad + 0.8D)) {
						this.aad += 2.0D;
					} else {
						mc.thePlayer.motionY = 0.7D;
						if(currentMode.equals("AAC2")) {
							mc.thePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY + 0.1, mc.thePlayer.posZ);
							mc.thePlayer.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX,
									mc.thePlayer.posY + 0.1, mc.thePlayer.posZ, true));
						}
						gijabgioagbpwigbpihbpisbsrlkgbaoighbaig(0.8f);
					}
				}
				else {
					if(currentMode.equals("AAC2")) {
						if(mc.thePlayer.motionY <= 0) {
							event.y = 0.01;
						}
					}
				}
			} else {
				mc.thePlayer.capabilities.isFlying = false;
			}
		}
		if (this.currentMode.equals("AAC3") && Start) {
			if (!Up) {
				//event.y = 0.01;
				mc.thePlayer.motionY = 1;
				Up = true;
			}
			else {
				event.y = -0.05;
				event.x *= 3;
				event.z *= 3;
				Up = false;
				mc.thePlayer.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX,
						mc.thePlayer.posY, mc.thePlayer.posZ, true));
			}
		}
	}

	public void gijabgioagbpwigbpihbpisbsrlkgbaoighbaig(float speed) {
		mc.thePlayer.motionX = (-(Math.sin(aan()) * speed));
		mc.thePlayer.motionZ = (Math.cos(aan()) * speed);
	}

	public float aan() {
		float var1 = mc.thePlayer.rotationYaw;
		if (mc.thePlayer.moveForward < 0.0F) {
			var1 += 180.0F;
		}
		float forward = 1.0F;
		if (mc.thePlayer.moveForward < 0.0F) {
			forward = -0.5F;
		} else if (mc.thePlayer.moveForward > 0.0F) {
			forward = 0.5F;
		}
		if (mc.thePlayer.moveStrafing > 0.0F) {
			var1 -= 90.0F * forward;
		}
		if (mc.thePlayer.moveStrafing < 0.0F) {
			var1 += 90.0F * forward;
		}
		var1 *= 0.017453292F;

		return var1;
	}

	@Override
	public ModSetting[] getModSettings() {
		SliderSetting slider1 = new SliderSetting("Flight Speed", "FlightdefaultSpeed", 0.1, 9, 0.0, ValueFormat.DECIMAL);
		CheckBtnSetting box2 = new CheckBtnSetting("Smooth Flight", "Flightsmooth");
		CheckBtnSetting box1 = new CheckBtnSetting("Vanilla Kick Bypass", "flightkick");
		CheckBtnSetting box3 = new CheckBtnSetting("Glide Mode - Damage", "glideDmg");
		CheckBtnSetting box4 = new CheckBtnSetting("onGround Spoof", "onGroundSpoofFlight");
		return new ModSetting[] { slider1, box2, box1, box3, box4 };
	}

	@Override
	public void onEnable() {
		timer.reset();
		if (currentMode.equals("AirWalk")) {
			maxY = mc.thePlayer.posY + 0.01d;
			damaging = true;
			// Atlas.sendChatMessage(".damage");
			mc.thePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY + 0.01d, mc.thePlayer.posZ);
		}
		if (currentMode.equals("Glide")) {
			this.startingHeight = mc.thePlayer.posY + 1000;
			if(ClientSettings.glideDmg) {
				Jigsaw.sendChatMessage(".damage");
			}
		}
		if (currentMode.equalsIgnoreCase("MAC")) {
			wait = 6;
			mc.thePlayer.motionY = 0.25;
		}
		if(currentMode.equals("AAC3")) {
			Up = true;
			Start = false;
		}
		super.onEnable();
	}

	public void updateFlyHeight() {
		double h = 1;
		AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox().expand(0.0625, 0.0625, 0.0625);
		for (flyHeight = 0; flyHeight < mc.thePlayer.posY; flyHeight += h) {
			AxisAlignedBB nextBox = box.offset(0, -flyHeight, 0);

			if (mc.theWorld.checkBlockCollision(nextBox)) {
				if (h < 0.0625)
					break;

				flyHeight -= h;
				h /= 2;
			}
		}
	}

	public void goToGround() {
		if (flyHeight > 320)
			return;

		double minY = mc.thePlayer.posY - flyHeight;

		if (minY <= 0)
			return;

		for (double y = mc.thePlayer.posY; y > minY;) {
			y -= 9.9;
			if (y < minY)
				y = minY;

			C04PacketPlayerPosition packet = new C04PacketPlayerPosition(mc.thePlayer.posX, y, mc.thePlayer.posZ, true);
			mc.thePlayer.sendQueue.addToSendQueue(packet);
		}

		for (double y = minY; y < mc.thePlayer.posY;) {
			y += 9.9;
			if (y > mc.thePlayer.posY)
				y = mc.thePlayer.posY;

			C04PacketPlayerPosition packet = new C04PacketPlayerPosition(mc.thePlayer.posX, y, mc.thePlayer.posZ, true);
			mc.thePlayer.sendQueue.addToSendQueue(packet);
		}
	}

	@Override
	public void onUpdate(UpdateEvent event) {
		super.onUpdate(event);
		if(currentMode.equals("AAC3")) {
			event.onGround = true;
			Start = true;
		}
		if (currentMode.equalsIgnoreCase("Default")) {
			if (!ClientSettings.Flightsmooth) {
				mc.thePlayer.motionX = 0;
				mc.thePlayer.motionZ = 0;
			}
			mc.thePlayer.capabilities.isFlying = false;
			mc.thePlayer.motionY = 0;
			if (ClientSettings.Flightsmooth) {
				mc.thePlayer.jumpMovementFactor = (float) (ClientSettings.FlightdefaultSpeed / 10);
			} else {
				mc.thePlayer.jumpMovementFactor = (float) (ClientSettings.FlightdefaultSpeed);
			}

			if (mc.gameSettings.keyBindJump.isKeyDown()) {
				mc.thePlayer.motionY += ClientSettings.FlightdefaultSpeed / 2;
			}
			if (mc.gameSettings.keyBindSneak.isKeyDown()) {
				mc.thePlayer.motionY += -ClientSettings.FlightdefaultSpeed / 2;
			}
			if (ClientSettings.flightkick) {
				mc.thePlayer.sendQueue.addToSendQueue(new C03PacketPlayer(true));
				updateFlyHeight();
				if (flyHeight <= 290 && timer.hasTimeElapsed(500, true)
						|| flyHeight > 290 && timer.hasTimeElapsed(100, true)) {
					goToGround();
				}
			}
		}
		if (currentMode.equalsIgnoreCase("MAC")) {
			if (wait < 6) {
				wait++;
				if (mc.thePlayer.motionY < 0// If falling
						&& !mc.thePlayer.onGround) {
					mc.thePlayer.motionY = -MACvelY;
				}
				return;
			}
			if (mc.thePlayer.motionY < 0// If falling
					&& !mc.thePlayer.onGround) {
				if (mc.gameSettings.keyBindJump.isKeyDown()) {
					mc.thePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY + 2, mc.thePlayer.posZ);
					wait = 3;
					return;
				} else if (mc.gameSettings.keyBindSneak.pressed) {
					mc.thePlayer.motionY = -0.4;
					return;
				}
				mc.thePlayer.motionY = -MACvelY;

			}
			if (ClientSettings.flightkick) {
				mc.thePlayer.sendQueue.addToSendQueue(new C03PacketPlayer(true));
				updateFlyHeight();
				if (flyHeight <= 290 && timer.hasTimeElapsed(500, true)
						|| flyHeight > 290 && timer.hasTimeElapsed(100, true)) {
					goToGround();
				}
			}
		}
		if (currentMode.equalsIgnoreCase("Creative")) {
			mc.thePlayer.capabilities.isFlying = true;
			if (ClientSettings.flightkick) {
				mc.thePlayer.sendQueue.addToSendQueue(new C03PacketPlayer(true));
				updateFlyHeight();
				if (flyHeight <= 290 && timer.hasTimeElapsed(500, true)
						|| flyHeight > 290 && timer.hasTimeElapsed(100, true)) {
					goToGround();
				}
			}
		}
		if (currentMode.equals("Glide")) {
			// if (mc.thePlayer.onGround) {
			// this.startingHeight = mc.thePlayer.posY;
			// }
			if (!mc.thePlayer.onGround) {
				if (mc.gameSettings.keyBindJump.isKeyDown() && mc.thePlayer.posY + 0.5 < startingHeight) {
					mc.thePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY + 0.5, mc.thePlayer.posZ);
				} else if (mc.gameSettings.keyBindSneak.pressed) {
					mc.thePlayer.motionY = -0.2;
					return;
				}
				mc.thePlayer.motionY = -fallSpeed;
				
				if(simulateFall) {
					simulateFall = false;
					
					double posX = mc.thePlayer.posX;
					double posY = mc.thePlayer.posY;
					double posZ = mc.thePlayer.posZ;
					
					mc.thePlayer.sendQueue.addToSendQueue(new C04PacketPlayerPosition(posX, posY + 0.049D, posZ, false));
					mc.thePlayer.sendQueue.addToSendQueue(new C04PacketPlayerPosition(posX, posY, posZ, false));
					mc.thePlayer.sendQueue.addToSendQueue(new C04PacketPlayerPosition(posX, posY + 0.049D, posZ, false));
					mc.thePlayer.sendQueue.addToSendQueue(new C04PacketPlayerPosition(posX, posY, posZ, false));
					event.onGround = false;
				}
				else {
					simulateFall = true;
					event.onGround = true;
				}
				
			}
			if (ClientSettings.flightkick) {
				mc.thePlayer.sendQueue.addToSendQueue(new C03PacketPlayer(true));
				updateFlyHeight();
				if (flyHeight <= 290 && timer.hasTimeElapsed(500, true)
						|| flyHeight > 290 && timer.hasTimeElapsed(100, true)) {
					goToGround();
				}
			}
		}
		if (currentMode.equals("AirWalk")) {
			if (mc.thePlayer.posY <= maxY) {
				mc.thePlayer.motionY = 0;
			}
			if (ClientSettings.flightkick) {
				mc.thePlayer.sendQueue.addToSendQueue(new C03PacketPlayer(true));
				updateFlyHeight();
				if (flyHeight <= 290 && timer.hasTimeElapsed(500, true)
						|| flyHeight > 290 && timer.hasTimeElapsed(100, true)) {
					goToGround();
				}
			}
		}
		if(ClientSettings.onGroundSpoofFlight) {
			event.onGround = true;
		}
	}

	@Override
	public void onBasicUpdates() {
		if (damaging) {
			damaging = false;
		}

		if (currentMode.equals("AirWalk")) {
			if (mc.thePlayer.posY <= maxY) {
				mc.thePlayer.onGround = true;
				// mc.thePlayer.motionY = 0;
			} else {
				mc.thePlayer.onGround = false;
			}
		}
		super.onBasicUpdates();
	}

	@Override
	public String[] getModes() {
		return new String[] { "Default", "MAC", "Creative", "Glide", "AirWalk", "AAC", "AAC2", "AAC3" };
	}

	@Override
	public String getAddonText() {
		return currentMode;
	}

}