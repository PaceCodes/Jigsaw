package me.robbanrobbin.jigsaw.client.modules;

import java.util.ArrayList;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import me.robbanrobbin.jigsaw.client.WaitTimer;
import me.robbanrobbin.jigsaw.client.module.state.Category;
import me.robbanrobbin.jigsaw.client.tools.RenderTools;
import me.robbanrobbin.jigsaw.module.Module;
import net.minecraft.util.math.Vec3d;

public class Breadcrumbs extends Module {
	WaitTimer timer = new WaitTimer();
	ArrayList<Vec3d> positions = new ArrayList<Vec3d>();

	public Breadcrumbs() {
		super("Breadcrumbs", Keyboard.KEY_NONE, Category.RENDER,
				"Renders a line that keeps track of where you have been.");
	}

	@Override
	public void onDisable() {
		positions.clear();
		super.onDisable();
	}

	@Override
	public void onEnable() {
		super.onEnable();
	}

	@Override
	public void onUpdate() {

		if (!timer.hasTimeElapsed(100, true)) {
			return;
		}
		if (mc.player.movementInput.moveForward == 0 && mc.player.moveStrafing == 0) {
			return;
		}
		positions.add(mc.player.getPositionVector());

		super.onUpdate();
	}

	@Override
	public void onRender() {
		GL11.glPushMatrix();
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_LINE_SMOOTH);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glBlendFunc(770, 771);
		GL11.glEnable(GL11.GL_BLEND);
		RenderTools.lineWidth(2);
		RenderTools.color4f(0.3f, 1f, 1f, 0.7f);
		RenderTools.glBegin(3);
		for (Vec3d vec : positions) {
			RenderTools.putVertex3d(RenderTools.getRenderPos(vec.x, vec.y + 0.3, vec.z));
		}
		RenderTools.glEnd();
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_LINE_SMOOTH);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glPopMatrix();

		super.onRender();
	}

}
