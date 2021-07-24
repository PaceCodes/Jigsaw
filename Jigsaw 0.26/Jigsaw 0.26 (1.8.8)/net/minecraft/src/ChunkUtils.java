package net.minecraft.src;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.chunk.Chunk;

public class ChunkUtils {
	private static Field fieldHasEntities = null;
	private static boolean fieldHasEntitiesMissing = false;

	public static boolean hasEntities(Chunk p_hasEntities_0_) {
		if (fieldHasEntities == null) {
			if (fieldHasEntitiesMissing) {
				return true;
			}

			fieldHasEntities = findFieldHasEntities(p_hasEntities_0_);

			if (fieldHasEntities == null) {
				fieldHasEntitiesMissing = true;
				return true;
			}
		}

		try {
			return fieldHasEntities.getBoolean(p_hasEntities_0_);
		} catch (Exception exception) {
			Config.warn("Error calling Chunk.hasEntities");
			Config.warn(exception.getClass().getName() + " " + exception.getMessage());
			fieldHasEntitiesMissing = true;
			return true;
		}
	}

	private static Field findFieldHasEntities(Chunk p_findFieldHasEntities_0_) {
		try {
			List<Field> list = new ArrayList();
			List list1 = new ArrayList();
			Field[] afield = Chunk.class.getDeclaredFields();

			for (int i = 0; i < afield.length; ++i) {
				Field field = afield[i];

				if (field.getType() == Boolean.TYPE) {
					field.setAccessible(true);
					list.add(field);
					list1.add(field.get(p_findFieldHasEntities_0_));
				}
			}

			p_findFieldHasEntities_0_.setHasEntities(false);
			List list2 = new ArrayList();

			for (Field field1 : list) {
				list2.add(field1.get(p_findFieldHasEntities_0_));
			}

			p_findFieldHasEntities_0_.setHasEntities(true);
			List list3 = new ArrayList();

			for (Field field2 : list) {
				list3.add(field2.get(p_findFieldHasEntities_0_));
			}

			List list4 = new ArrayList();

			for (int j = 0; j < ((List) list).size(); ++j) {
				Field field3 = (Field) list.get(j);
				Boolean obool = (Boolean) list2.get(j);
				Boolean obool1 = (Boolean) list3.get(j);

				if (!obool.booleanValue() && obool1.booleanValue()) {
					list4.add(field3);
					Boolean obool2 = (Boolean) list1.get(j);
					field3.set(p_findFieldHasEntities_0_, obool2);
				}
			}

			if (list4.size() == 1) {
				Field field4 = (Field) list4.get(0);
				return field4;
			}
		} catch (Exception exception) {
			Config.warn(exception.getClass().getName() + " " + exception.getMessage());
		}

		Config.warn("Error finding Chunk.hasEntities");
		return null;
	}
}
