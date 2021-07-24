package net.minecraft.client.resources;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.commons.io.filefilter.DirectoryFileFilter;

import com.google.common.base.CharMatcher;
import com.google.common.collect.Sets;

import net.minecraft.util.Util;

public class FolderResourcePack extends AbstractResourcePack
{
    private static final boolean field_191386_b = Util.getOSType() == Util.EnumOS.WINDOWS;
    private static final CharMatcher field_191387_c = CharMatcher.is('\\');

    public FolderResourcePack(File resourcePackFileIn)
    {
        super(resourcePackFileIn);
    }

    protected static boolean func_191384_a(File p_191384_0_, String p_191384_1_) throws IOException
    {
        String s = p_191384_0_.getCanonicalPath();

        if (field_191386_b)
        {
            s = field_191387_c.replaceFrom(s, '/');
        }

        return s.endsWith(p_191384_1_);
    }

    protected InputStream getInputStreamByName(String name) throws IOException
    {
        File file1 = this.func_191385_d(name);

        if (file1 == null)
        {
            throw new ResourcePackFileNotFoundException(this.resourcePackFile, name);
        }
        else
        {
            return new BufferedInputStream(new FileInputStream(file1));
        }
    }

    protected boolean hasResourceName(String name)
    {
        return this.func_191385_d(name) != null;
    }

    @Nullable
    private File func_191385_d(String p_191385_1_)
    {
        try
        {
            File file1 = new File(this.resourcePackFile, p_191385_1_);

            if (file1.isFile() && func_191384_a(file1, p_191385_1_))
            {
                return file1;
            }
        }
        catch (IOException var3)
        {
            ;
        }

        return null;
    }

    public Set<String> getResourceDomains()
    {
        Set<String> set = Sets.<String>newHashSet();
        File file1 = new File(this.resourcePackFile, "assets/");

        if (file1.isDirectory())
        {
            for (File file2 : file1.listFiles((FileFilter)DirectoryFileFilter.DIRECTORY))
            {
                String s = getRelativeName(file1, file2);

                if (s.equals(s.toLowerCase(java.util.Locale.ROOT)))
                {
                    set.add(s.substring(0, s.length() - 1));
                }
                else
                {
                    this.logNameNotLowercase(s);
                }
            }
        }

        return set;
    }
}
