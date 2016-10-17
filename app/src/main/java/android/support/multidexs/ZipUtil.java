//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package android.support.multidexs;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.zip.CRC32;
import java.util.zip.ZipException;

final class ZipUtil {
    private static final int ENDHDR      = 22;
    private static final int ENDSIG      = 101010256;
    private static final int BUFFER_SIZE = 16384;

    ZipUtil() {
    }

    static long getZipCrc(File apk) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(apk, "r");

        long var3;
        try {
            ZipUtil.CentralDirectory dir = findCentralDirectory(raf);
            var3 = computeCrcOfCentralDir(raf, dir);
        } finally {
            raf.close();
        }

        return var3;
    }

    static ZipUtil.CentralDirectory findCentralDirectory(RandomAccessFile raf) throws IOException, ZipException {
        long scanOffset = raf.length() - 22L;
        if (scanOffset < 0L) {
            throw new ZipException("File too short to be a zip file: " + raf.length());
        } else {
            long stopOffset = scanOffset - 65536L;
            if (stopOffset < 0L) {
                stopOffset = 0L;
            }

            int endSig = Integer.reverseBytes(101010256);

            do {
                raf.seek(scanOffset);
                if (raf.readInt() == endSig) {
                    raf.skipBytes(2);
                    raf.skipBytes(2);
                    raf.skipBytes(2);
                    raf.skipBytes(2);
                    ZipUtil.CentralDirectory dir = new ZipUtil.CentralDirectory();
                    dir.size = (long) Integer.reverseBytes(raf.readInt()) & 4294967295L;
                    dir.offset = (long) Integer.reverseBytes(raf.readInt()) & 4294967295L;
                    return dir;
                }

                --scanOffset;
            } while (scanOffset >= stopOffset);

            throw new ZipException("End Of Central Directory signature not found");
        }
    }

    static long computeCrcOfCentralDir(RandomAccessFile raf, ZipUtil.CentralDirectory dir) throws IOException {
        CRC32 crc = new CRC32();
        long stillToRead = dir.size;
        raf.seek(dir.offset);
        int length = (int) Math.min(16384L, stillToRead);
        byte[] buffer = new byte[16384];

        for (length = raf.read(buffer, 0, length); length != -1; length = raf.read(buffer, 0, length)) {
            crc.update(buffer, 0, length);
            stillToRead -= (long) length;
            if (stillToRead == 0L) {
                break;
            }

            length = (int) Math.min(16384L, stillToRead);
        }

        return crc.getValue();
    }

    static class CentralDirectory {
        long offset;
        long size;

        CentralDirectory() {
        }
    }
}
