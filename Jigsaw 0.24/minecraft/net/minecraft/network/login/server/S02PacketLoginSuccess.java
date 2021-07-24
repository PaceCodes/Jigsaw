package net.minecraft.network.login.server;

import java.io.IOException;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.network.AbstractPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.login.INetHandlerLoginClient;

public class S02PacketLoginSuccess extends AbstractPacket<INetHandlerLoginClient> {
	private GameProfile profile;

	public S02PacketLoginSuccess() {
	}

	public S02PacketLoginSuccess(GameProfile profileIn) {
		this.profile = profileIn;
	}

	/**
	 * Reads the raw packet data from the data stream.
	 */
	public void readPacketData(PacketBuffer buf) throws IOException {
		String s = buf.readStringFromBuffer(36);
		String s1 = buf.readStringFromBuffer(16);
		UUID uuid = UUID.fromString(s);
		this.profile = new GameProfile(uuid, s1);
	}

	/**
	 * Writes the raw packet data to the data stream.
	 */
	public void writePacketData(PacketBuffer buf) throws IOException {
		UUID uuid = this.profile.getId();
		buf.writeString(uuid == null ? "" : uuid.toString());
		buf.writeString(this.profile.getName());
	}

	/**
	 * Passes this Packet on to the NetHandler for processing.
	 */
	public void processPacket(INetHandlerLoginClient handler) {
		super.processPacket(handler);
		if (this.cancelled) {
			return;
		}
		handler.handleLoginSuccess(this);
	}

	public GameProfile getProfile() {
		return this.profile;
	}
}
