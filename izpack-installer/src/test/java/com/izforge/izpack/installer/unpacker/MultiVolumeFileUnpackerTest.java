package com.izforge.izpack.installer.unpacker;


import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;

import org.junit.Test;

import com.izforge.izpack.api.data.Blockable;
import com.izforge.izpack.api.data.OverrideType;
import com.izforge.izpack.api.data.PackFile;
import com.izforge.izpack.api.data.XPackFile;
import com.izforge.izpack.api.exception.InstallerException;
import com.izforge.izpack.core.io.FileSpanningInputStream;
import com.izforge.izpack.core.io.FileSpanningOutputStream;
import com.izforge.izpack.core.io.VolumeLocator;
import com.izforge.izpack.installer.multiunpacker.MultiVolumeFileUnpacker;
import com.izforge.izpack.util.IoHelper;
import com.izforge.izpack.util.Platforms;
import com.izforge.izpack.util.os.FileQueue;


/**
 * Tests the {@link MultiVolumeFileUnpacker} class.
 *
 * @author Tim Anderson
 */
public class MultiVolumeFileUnpackerTest extends AbstractFileUnpackerTest
{

    /**
     * The first volume.
     */
    private File volume;

    /**
     * The no. of volumes.
     */
    private int volumeCount;

    /**
     * Verifies that the {@link VolumeLocator#getVolume(String, boolean)} method is invoked to prompt
     * for missing media.
     *
     * @throws IOException        for any I/O error
     * @throws InstallerException for any installer error
     */
    @Test
    public void testPromptForNextMedia() throws IOException, InstallerException
    {
        File baseDir = temporaryFolder.getRoot();
        File source = createSourceFile(baseDir);
        File target = getTargetFile(baseDir);

        // rename the first volume so that it is prompted for
        final File volume1 = new File(volume.getPath() + ".1");
        assertTrue(volume1.exists());
        final File renamed = new File(volume1.getPath() + ".bak");
        assertTrue(volume1.renameTo(renamed));

        VolumeLocator locator = new VolumeLocator()
        {
            @Override
            public File getVolume(String path, boolean corrupt) throws IOException
            {
                // rename the file back
                assertTrue(renamed.renameTo(volume1));
                return volume1;
            }
        };

        FileSpanningInputStream stream = new FileSpanningInputStream(volume, volumeCount);
        stream.setLocator(locator);
        FileUnpacker unpacker = new MultiVolumeFileUnpacker(stream, getCancellable(), getHandler(), null,
                                                            Platforms.WINDOWS, getLibrarian());

        PackFile file = createPackFile(baseDir, source, target, Blockable.BLOCKABLE_NONE);
        assertFalse(target.exists());

        ObjectInputStream packStream = createPackStream(source);
        FileQueue queue = unpacker.unpack(file, packStream, target);
        assertNull(queue);  // file should not have been queued

        // verify the file unpacked successfully
        checkTarget(source, target);
    }

    /**
     * Creates a new source file.
     *
     * @param baseDir the base directory
     * @return the source file
     * @throws java.io.IOException for any I/O error
     */
    @Override
    protected File createSourceFile(File baseDir) throws IOException
    {
        File source = new File(baseDir, "source.txt");
        PrintWriter stream = new PrintWriter(source);
        for (int i = 0; i < 20000; ++i)
        {
            stream.println(i);
        }
        assertFalse(stream.checkError());
        stream.close();

        volume = new File(temporaryFolder.getRoot(), "volume");
        FileSpanningOutputStream out = new FileSpanningOutputStream(volume, 8192);
        FileInputStream in = new FileInputStream(source);
        IoHelper.copyStream(in, out);

        // verify there is more than one volume
        out.flush();
        volumeCount = out.getVolumes();
        assertTrue(volumeCount > 1);
        in.close();
        out.close();
        return source;
    }

    /**
     * Helper to create an unpacker.
     *
     * @param sourceDir the source directory
     * @return a new unpacker
     */
    protected FileUnpacker createUnpacker(File sourceDir) throws IOException
    {
        FileSpanningInputStream stream = new FileSpanningInputStream(volume, volumeCount);
        return new MultiVolumeFileUnpacker(stream, getCancellable(), getHandler(), null, Platforms.WINDOWS,
                                           getLibrarian());
    }

    /**
     * Helper to create a new pack file.
     *
     * @param baseDir   the base directory
     * @param source    the source file
     * @param target    the target file
     * @param blockable the blockable type
     * @return a new pack file
     * @throws IOException if the source file doesn't exist
     */
    @Override
    protected PackFile createPackFile(File baseDir, File source, File target, Blockable blockable) throws IOException
    {
        // XPackFile required for the Archivefileposition attribute.
        XPackFile result = new XPackFile(baseDir, source, target.getName(), null, OverrideType.OVERRIDE_TRUE, null,
                                         blockable);
        result.setArchivefileposition(0);
        return result;
    }
}