/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util;

import org.gridgain.grid.*;
import org.gridgain.grid.compute.*;
import org.gridgain.grid.util.lang.*;
import org.gridgain.grid.util.typedef.*;
import org.gridgain.grid.util.typedef.internal.*;
import org.gridgain.testframework.*;
import org.gridgain.testframework.http.*;
import org.gridgain.testframework.junits.*;
import org.gridgain.testframework.junits.common.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.lang.annotation.*;
import java.math.*;
import java.net.*;
import java.nio.*;
import java.util.*;

/**
 * Grid utils tests.
 */
@GridCommonTest(group = "Utils")
public class GridUtilsSelfTest extends GridCommonAbstractTest {
    /**
     * @return 120 character length string.
     */
    private String text120() {
        char[] chs = new char[120];

        Arrays.fill(chs, 'x');

        return new String(chs);
    }

    /**
     *
     */
    public void testIsPow2() {
        assertTrue(U.isPow2(1));
        assertTrue(U.isPow2(2));
        assertTrue(U.isPow2(4));
        assertTrue(U.isPow2(8));
        assertTrue(U.isPow2(16));
        assertTrue(U.isPow2(16 * 16));
        assertTrue(U.isPow2(32 * 32));

        assertFalse(U.isPow2(-4));
        assertFalse(U.isPow2(-3));
        assertFalse(U.isPow2(-2));
        assertFalse(U.isPow2(-1));
        assertFalse(U.isPow2(0));
        assertFalse(U.isPow2(3));
        assertFalse(U.isPow2(5));
        assertFalse(U.isPow2(6));
        assertFalse(U.isPow2(7));
        assertFalse(U.isPow2(9));
    }

    /**
     * @throws Exception If failed.
     */
    public void testAllLocalIps() throws Exception {
        Collection<String> ips = U.allLocalIps();

        System.out.println("All local IPs: " + ips);
    }

    /**
     * @throws Exception If failed.
     */
    public void testAllLocalMACs() throws Exception {
        Collection<String> macs = U.allLocalMACs();

        System.out.println("All local MACs: " + macs);
    }

    /**
     * On linux NetworkInterface.getHardwareAddress() returns null from time to time.
     *
     * @throws Exception If failed.
     */
    public void testAllLocalMACsMultiThreaded() throws Exception {
        GridTestUtils.runMultiThreaded(new Runnable() {
            @Override public void run() {
                for (int i = 0; i < 30; i++) {
                    Collection<String> macs = U.allLocalMACs();

                    assertTrue("Mac address are not defined.", !macs.isEmpty());
                }
            }
        }, 32, "thread");
    }

    /**
     * @throws Exception If failed.
     */
    public void testByteArray2String() throws Exception {
        assertEquals("{0x0A,0x14,0x1E,0x28,0x32,0x3C,0x46,0x50,0x5A}",
            U.byteArray2String(new byte[] {10, 20, 30, 40, 50, 60, 70, 80, 90}, "0x%02X", ",0x%02X"));
    }

    /**
     * @throws Exception If failed.
     */
    public void testFormatMins() throws Exception {
        printFormatMins(0);
        printFormatMins(1);
        printFormatMins(2);
        printFormatMins(59);
        printFormatMins(60);
        printFormatMins(61);
        printFormatMins(60 * 24 - 1);
        printFormatMins(60 * 24);
        printFormatMins(60 * 24 + 1);
        printFormatMins(5 * 60 * 24 - 1);
        printFormatMins(5 * 60 * 24);
        printFormatMins(5 * 60 * 24 + 1);
    }

    /**
     * Helper method for {@link #testFormatMins()}
     *
     * @param mins Minutes to test.
     */
    private void printFormatMins(long mins) {
        System.out.println("For " + mins + " minutes: " + X.formatMins(mins));
    }

    /**
     * @throws Exception If failed.
     */
    public void testDownloadUrlFromHttp() throws Exception {
        GridEmbeddedHttpServer srv = null;
        try {
            String urlPath = "/testDownloadUrl/";
            srv = GridEmbeddedHttpServer.startHttpServer().withFileDownloadingHandler(urlPath,
                GridTestUtils.resolveGridGainPath("/modules/core/src/test/config/tests.properties"));

            File file = new File(System.getProperty("java.io.tmpdir") + File.separator + "url-http.file");

            file = U.downloadUrl(new URL(srv.getBaseUrl() + urlPath), file);

            assert file.exists();
            assert file.delete();
        }
        finally {
            if (srv != null)
                srv.stop(1);
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testDownloadUrlFromHttps() throws Exception {
        GridEmbeddedHttpServer srv = null;
        try {
            String urlPath = "/testDownloadUrl/";
            srv = GridEmbeddedHttpServer.startHttpsServer().withFileDownloadingHandler(urlPath,
                GridTestUtils.resolveGridGainPath("modules/core/src/test/config/tests.properties"));

            File file = new File(System.getProperty("java.io.tmpdir") + File.separator + "url-http.file");

            file = U.downloadUrl(new URL(srv.getBaseUrl() + urlPath), file);

            assert file.exists();
            assert file.delete();
        }
        finally {
            if (srv != null)
                srv.stop(1);
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testDownloadUrlFromLocalFile() throws Exception {
        File file = new File(System.getProperty("java.io.tmpdir") + File.separator + "url-http.file");

        file = U.downloadUrl(
            GridTestUtils.resolveGridGainPath("modules/core/src/test/config/tests.properties").toURI().toURL(), file);

        assert file.exists();
        assert file.delete();
    }

    /**
     * @throws Exception If failed.
     */
    public void testOs() throws Exception {
        System.out.println("OS string: " + U.osString());
        System.out.println("JDK string: " + U.jdkString());
        System.out.println("OS/JDK string: " + U.osJdkString());

        System.out.println("Is Windows: " + U.isWindows());
        System.out.println("Is Windows 95: " + U.isWindows95());
        System.out.println("Is Windows 98: " + U.isWindows98());
        System.out.println("Is Windows NT: " + U.isWindowsNt());
        System.out.println("Is Windows 2000: " + U.isWindows2k());
        System.out.println("Is Windows 2003: " + U.isWindows2003());
        System.out.println("Is Windows XP: " + U.isWindowsXp());
        System.out.println("Is Windows Vista: " + U.isWindowsVista());
        System.out.println("Is Linux: " + U.isLinux());
        System.out.println("Is Mac OS: " + U.isMacOs());
        System.out.println("Is Netware: " + U.isNetWare());
        System.out.println("Is Solaris: " + U.isSolaris());
        System.out.println("Is Solaris SPARC: " + U.isSolarisSparc());
        System.out.println("Is Solaris x86: " + U.isSolarisX86());
        System.out.println("Is Windows7: " + U.isWindows7());
        System.out.println("Is Sufficiently Tested OS: " + U.isSufficientlyTestedOs());
    }

    /**
     * @throws Exception If failed.
     */
    public void testJavaSerialization() throws Exception {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        ObjectOutputStream objOut = new ObjectOutputStream(byteOut);

        objOut.writeObject(new byte[] {1, 2, 3, 4, 5, 5});

        objOut.flush();

        byte[] sBytes = byteOut.toByteArray();

        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(sBytes));

        in.readObject();
    }

    /**
     *
     */
    public void testHidePassword() {
        Collection<String> uriList = new ArrayList<>();

        uriList.add("ftp://anonymous:111111;freq=5000@unknown.host:21/pub/gg-test");
        uriList.add("ftp://anonymous:111111;freq=5000@localhost:21/pub/gg-test");

        uriList.add("http://freq=5000@localhost/tasks");
        uriList.add("http://freq=5000@unknownhost.host/tasks");

        for (String uri : uriList)
            X.println(uri + " -> " + U.hidePassword(uri));
    }

    /**
     * Test job to test possible indefinite recursion in detecting peer deploy aware.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    private class SelfReferencedJob extends GridComputeJobAdapter implements GridPeerDeployAware {
        /** */
        private SelfReferencedJob ref;

        /** */
        private SelfReferencedJob[] arr;

        /** */
        private Collection<SelfReferencedJob> col;

        /** */
        private GridNode node;

        /** */
        private GridProjection subGrid;

        /**
         * @param grid Grid.
         */
        private SelfReferencedJob(Grid grid) {
            node = grid.localNode();

            ref = this;

            arr = new SelfReferencedJob[] {this, this};

            col = Arrays.asList(this, this, this);

            GridTestKernalContext ctx = newContext();

            subGrid = grid.forNodes(Collections.singleton(node));
        }

        /** {@inheritDoc} */
        @Override public Object execute() {
            return null;
        }

        /** {@inheritDoc} */
        @Override public Class<?> deployClass() {
            return getClass();
        }

        /** {@inheritDoc} */
        @Override public ClassLoader classLoader() {
            return getClass().getClassLoader();
        }
    }

    /**
     * @throws Exception If test fails.
     */
    public void testDetectPeerDeployAwareInfiniteRecursion() throws Exception {
        Grid g = startGrid(1);

        try {
            final SelfReferencedJob job = new SelfReferencedJob(g);

            GridPeerDeployAware d = U.detectPeerDeployAware(U.peerDeployAware(job));

            assert d != null;
            assert SelfReferencedJob.class == d.deployClass();
            assert d.classLoader() == SelfReferencedJob.class.getClassLoader();
        }
        finally {
            stopGrid(1);
        }
    }

    /**
     * @param r Runnable.
     * @return Job created for given runnable.
     */
    private static GridComputeJob job(final Runnable r) {
        return new GridComputeJobAdapter() {
            @Nullable @Override public Object execute() {
                r.run();

                return null;
            }
        };
    }

    /**
     *
     * @throws Exception If failed.
     */
    public void testParseIsoDate() throws Exception {
        Calendar cal = U.parseIsoDate("2009-12-08T13:30:44.000Z");

        assert cal.get(Calendar.YEAR) == 2009;
        assert cal.get(Calendar.MONTH) == 11;
        assert cal.get(Calendar.DAY_OF_MONTH) == 8;
        assert cal.get(Calendar.HOUR_OF_DAY) == 13;
        assert cal.get(Calendar.MINUTE) == 30;
        assert cal.get(Calendar.SECOND) == 44;
        assert cal.get(Calendar.MILLISECOND) == 0;
        assert cal.get(Calendar.ZONE_OFFSET) == 0 :
            "Unexpected value: " + cal.get(Calendar.ZONE_OFFSET);

        cal = U.parseIsoDate("2009-12-08T13:30:44.000+03:00");

        assert cal.get(Calendar.YEAR) == 2009;
        assert cal.get(Calendar.MONTH) == 11;
        assert cal.get(Calendar.DAY_OF_MONTH) == 8;
        assert cal.get(Calendar.HOUR_OF_DAY) == 13;
        assert cal.get(Calendar.MINUTE) == 30;
        assert cal.get(Calendar.SECOND) == 44;
        assert cal.get(Calendar.MILLISECOND) == 0;
        assert cal.get(Calendar.ZONE_OFFSET) == 3 * 60 * 60 * 1000 :
            "Unexpected value: " + cal.get(Calendar.ZONE_OFFSET);

        cal = U.parseIsoDate("2009-12-08T13:30:44.000+0300");

        assert cal.get(Calendar.YEAR) == 2009;
        assert cal.get(Calendar.MONTH) == 11;
        assert cal.get(Calendar.DAY_OF_MONTH) == 8;
        assert cal.get(Calendar.HOUR_OF_DAY) == 13;
        assert cal.get(Calendar.MINUTE) == 30;
        assert cal.get(Calendar.SECOND) == 44;
        assert cal.get(Calendar.MILLISECOND) == 0;
        assert cal.get(Calendar.ZONE_OFFSET) == 3 * 60 * 60 * 1000 :
            "Unexpected value: " + cal.get(Calendar.ZONE_OFFSET);
    }

    /**
     * @throws Exception If test failed.
     */
    public void testPeerDeployAware0() throws Exception {
        Collection<Object> col = new ArrayList<>();

        col.add(null);
        col.add(null);
        col.add(null);

        GridPeerDeployAware pda = U.peerDeployAware0(col);

        assert pda != null;

        col.clear();

        col.add(null);

        pda = U.peerDeployAware0(col);

        assert pda != null;

        col.clear();

        pda = U.peerDeployAware0(col);

        assert pda != null;

        col.clear();

        col.add(null);
        col.add("Test");
        col.add(null);

        pda = U.peerDeployAware0(col);

        assert pda != null;

        col.clear();

        col.add("Test");

        pda = U.peerDeployAware0(col);

        assert pda != null;

        col.clear();

        col.add("Test");
        col.add(this);

        pda = U.peerDeployAware0(col);

        assert pda != null;

        col.clear();

        col.add(null);
        col.add("Test");
        col.add(null);
        col.add(this);
        col.add(null);

        pda = U.peerDeployAware0(col);

        assert pda != null;
    }

    /**
     * Test UUID to bytes array conversion.
     */
    public void testsGetBytes() {
        for (int i = 0; i < 100; i++) {
            UUID id = UUID.randomUUID();

            byte[] bytes = GridUtils.uuidToBytes(id);
            BigInteger n = new BigInteger(bytes);

            assert n.shiftRight(Long.SIZE).longValue() == id.getMostSignificantBits();
            assert n.longValue() == id.getLeastSignificantBits();
        }
    }

    /**
     *
     */
    @SuppressWarnings("ZeroLengthArrayAllocation")
    public void testReadByteArray() {
        assertTrue(Arrays.equals(new byte[0], U.readByteArray(ByteBuffer.allocate(0))));
        assertTrue(Arrays.equals(new byte[0], U.readByteArray(ByteBuffer.allocate(0), ByteBuffer.allocate(0))));

        Random rnd = new Random();

        byte[] bytes = new byte[13];

        rnd.nextBytes(bytes);

        assertTrue(Arrays.equals(bytes, U.readByteArray(ByteBuffer.wrap(bytes))));
        assertTrue(Arrays.equals(bytes, U.readByteArray(ByteBuffer.wrap(bytes), ByteBuffer.allocate(0))));
        assertTrue(Arrays.equals(bytes, U.readByteArray(ByteBuffer.allocate(0), ByteBuffer.wrap(bytes))));

        for (int i = 0; i < 1000; i++) {
            int n = rnd.nextInt(100);

            bytes = new byte[n];

            rnd.nextBytes(bytes);

            ByteBuffer[] bufs = new ByteBuffer[1 + rnd.nextInt(10)];

            int x = 0;

            for (int j = 0; j < bufs.length - 1; j++) {
                int size = x == n ? 0 : rnd.nextInt(n - x);

                bufs[j] = (ByteBuffer)ByteBuffer.wrap(bytes).position(x).limit(x += size);
            }

            bufs[bufs.length - 1] = (ByteBuffer)ByteBuffer.wrap(bytes).position(x).limit(n);

            assertTrue(Arrays.equals(bytes, U.readByteArray(bufs)));
        }
    }

    /**
     *
     */
    @SuppressWarnings("ZeroLengthArrayAllocation")
    public void testHashCodeFromBuffers() {
        assertEquals(Arrays.hashCode(new byte[0]), U.hashCode(ByteBuffer.allocate(0)));
        assertEquals(Arrays.hashCode(new byte[0]), U.hashCode(ByteBuffer.allocate(0), ByteBuffer.allocate(0)));

        Random rnd = new Random();

        for (int i = 0; i < 1000; i++) {
            ByteBuffer[] bufs = new ByteBuffer[1 + rnd.nextInt(15)];

            for (int j = 0; j < bufs.length; j++) {
                byte[] bytes = new byte[rnd.nextInt(25)];

                rnd.nextBytes(bytes);

                bufs[j] = ByteBuffer.wrap(bytes);
            }

            assertEquals(U.hashCode(bufs), Arrays.hashCode(U.readByteArray(bufs)));
        }
    }

    /**
     * Test annotation look up.
     */
    public void testGetAnnotations() {
        assert U.getAnnotation(A1.class, Ann1.class) != null;
        assert U.getAnnotation(A2.class, Ann1.class) != null;

        assert U.getAnnotation(A1.class, Ann2.class) != null;
        assert U.getAnnotation(A2.class, Ann2.class) != null;

        assert U.getAnnotation(A3.class, Ann1.class) == null;
        assert U.getAnnotation(A3.class, Ann2.class) != null;
    }

    /**
     * Test enum.
     */
    private enum TestEnum {
        E1,
        E2,
        E3
    }

    @Documented @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
    private @interface Ann1 {}

    @Documented @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
    private @interface Ann2 {}

    private static class A1 implements I3, I5 {}
    private static class A2 extends A1 {}
    private static class A3 implements I5 {}

    @Ann1 private interface I1 {}
    private interface I2 extends I1 {}
    private interface I3 extends I2 {}
    @Ann2 private interface I4 {}
    private interface I5 extends I4 {}
}