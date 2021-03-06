package de.gerrygames.viarewind.protocol.protocol1_8to1_9.chunks;

import de.gerrygames.viarewind.protocol.protocol1_8to1_9.Protocol1_8TO1_9;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import us.myles.ViaVersion.api.PacketWrapper;
import us.myles.ViaVersion.api.data.UserConnection;
import us.myles.ViaVersion.api.minecraft.Environment;
import us.myles.ViaVersion.api.minecraft.Position;
import us.myles.ViaVersion.api.minecraft.chunks.Chunk;
import us.myles.ViaVersion.api.minecraft.chunks.ChunkSection;
import us.myles.ViaVersion.api.type.Type;
import us.myles.ViaVersion.api.type.types.CustomByteType;
import us.myles.ViaVersion.exception.CancelException;
import us.myles.ViaVersion.protocols.protocol1_9_3to1_9_1_2.storage.ClientWorld;
import us.myles.ViaVersion.protocols.protocol1_9_3to1_9_1_2.types.Chunk1_9_1_2Type;

public class ChunkPacketTransformer {
	public static void transformChunk(PacketWrapper packetWrapper) throws Exception {
		ClientWorld world = packetWrapper.user().get(ClientWorld.class);
		Chunk1_8to1_9 chunk;
		int chunkX, chunkZ, primaryBitMask;
		boolean groundUp;

		if (world!=null) {
			Chunk chunk1_9 = packetWrapper.read(new Chunk1_9_1_2Type(world));
			chunkX = chunk1_9.getX();
			chunkZ = chunk1_9.getZ();
			boolean skyLight = world.getEnvironment()==Environment.NORMAL;
			primaryBitMask = chunk1_9.getBitmask();

			ByteBuf data = Unpooled.buffer();
			for (int i = 0; i < chunk1_9.getSections().length; i++) {
				if ((primaryBitMask & 1 << i) != 0) {
					ChunkSection section = chunk1_9.getSections()[i];
					section.writeBlocks(data);
					section.writeBlockLight(data);
					if (skyLight) section.writeSkyLight(data);
				}
			}
			byte[] rawdata = new byte[data.readableBytes()];
			data.readBytes(rawdata);
			data.release();

			chunk = new Chunk1_8to1_9(rawdata, primaryBitMask, skyLight, groundUp = chunk1_9.isGroundUp(), chunk1_9.getBiomeData());

			final UserConnection user = packetWrapper.user();
			chunk1_9.getBlockEntities().forEach(nbt ->  {
				if (!nbt.contains("x") || !nbt.contains("y") || !nbt.contains("z") || !nbt.contains("id")) return;
				Position position = new Position((long)(int)nbt.get("x").getValue(), (long)(int)nbt.get("y").getValue(), (long)(int)nbt.get("z").getValue());
				String id = (String)nbt.get("id").getValue();

				short action;
				switch (id) {
					case "minecraft:mob_spawner": action = 1; break;
					case "minecraft:command_block": action = 2; break;
					case "minecraft:beacon": action = 3; break;
					case "minecraft:skull": action = 4; break;
					case "minecraft:flower_pot": action = 5; break;
					case "minecraft:banner": action = 6; break;
					default: return;
				}

				PacketWrapper updateTileEntity = new PacketWrapper(0x09, null, user);
				updateTileEntity.write(Type.POSITION, position);
				updateTileEntity.write(Type.UNSIGNED_BYTE, action);
				updateTileEntity.write(Type.NBT, nbt);

				try {
					updateTileEntity.send(Protocol1_8TO1_9.class, false, false);
				} catch (CancelException ignored) {
					;
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			});
		} else {
			chunkX = packetWrapper.read(Type.INT);
			chunkZ = packetWrapper.read(Type.INT);
			groundUp = packetWrapper.read(Type.BOOLEAN);
			primaryBitMask = packetWrapper.read(Type.VAR_INT);
			int size = packetWrapper.read(Type.VAR_INT);
			if (groundUp) size -= 256;
			CustomByteType customByteType = new CustomByteType(size);
			byte[] data = packetWrapper.read(customByteType);
			byte[] biomes = groundUp ? packetWrapper.read(new CustomByteType(256)) : new byte[0];

			chunk = new Chunk1_8to1_9(data, primaryBitMask, true, groundUp, biomes);
		}

		if (groundUp && primaryBitMask==0) {
			chunk.primaryBitMask = primaryBitMask = 65535;
			chunk.fillAir();
		}

		packetWrapper.write(Type.INT, chunkX);
		packetWrapper.write(Type.INT, chunkZ);
		packetWrapper.write(Type.BOOLEAN, groundUp);
		packetWrapper.write(Type.UNSIGNED_SHORT, primaryBitMask);
		byte[] finaldata = chunk.get1_8Data();
		packetWrapper.write(Type.VAR_INT, finaldata.length);
		packetWrapper.write(new CustomByteType(finaldata.length), finaldata);
	}
}
