package me.robbanrobbin.jigsaw.client.modules;

import org.lwjgl.input.Keyboard;

import me.robbanrobbin.jigsaw.client.main.Jigsaw;
import me.robbanrobbin.jigsaw.client.module.state.Category;
import me.robbanrobbin.jigsaw.gui.ScreenPos;
import me.robbanrobbin.jigsaw.module.Module;

public class TPS extends Module {

	public TPS() {
		super("TPS", Keyboard.KEY_NONE, Category.PLAYER,
				"Shows the server's TPS (Ticks per second). Lower TPS means more lag!");
	}

	@Override
	public void onDisable() {
		
		super.onDisable();
	}

	@Override
	public void onUpdate() {
		

		super.onUpdate();
	}

	@Override
	public void onRender() {

		Jigsaw.getUIManager().addToQueue(String.valueOf("§jTPS: §r" + Math.round(Jigsaw.lastTps * 100.0) / 100.0), ScreenPos.LEFTUP);

		super.onRender();
	}

}
