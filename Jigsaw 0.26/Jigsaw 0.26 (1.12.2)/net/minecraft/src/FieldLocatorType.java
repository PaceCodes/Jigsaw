package net.minecraft.src;

import java.lang.reflect.Field;

public class FieldLocatorType implements IFieldLocator
{
    private ReflectorClass reflectorClass;
    private Class targetFieldType;
    private int targetFieldIndex;

    public FieldLocatorType(ReflectorClass p_i39_1_, Class p_i39_2_)
    {
        this(p_i39_1_, p_i39_2_, 0);
    }

    public FieldLocatorType(ReflectorClass p_i40_1_, Class p_i40_2_, int p_i40_3_)
    {
        this.reflectorClass = null;
        this.targetFieldType = null;
        this.reflectorClass = p_i40_1_;
        this.targetFieldType = p_i40_2_;
        this.targetFieldIndex = p_i40_3_;
    }

    public Field getField()
    {
        Class oclass = this.reflectorClass.getTargetClass();

        if (oclass == null)
        {
            return null;
        }
        else
        {
            try
            {
                Field[] afield = oclass.getDeclaredFields();
                int i = 0;

                for (int j = 0; j < afield.length; ++j)
                {
                    Field field = afield[j];

                    if (field.getType() == this.targetFieldType)
                    {
                        if (i == this.targetFieldIndex)
                        {
                            field.setAccessible(true);
                            return field;
                        }

                        ++i;
                    }
                }

                Config.log("(Reflector) Field not present: " + oclass.getName() + ".(type: " + this.targetFieldType + ", index: " + this.targetFieldIndex + ")");
                return null;
            }
            catch (SecurityException securityexception)
            {
                securityexception.printStackTrace();
                return null;
            }
            catch (Throwable throwable)
            {
                throwable.printStackTrace();
                return null;
            }
        }
    }
}
