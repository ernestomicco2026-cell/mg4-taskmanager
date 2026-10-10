package it.mg4.taskmanager;

import android.content.Context;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * Client minimale del protocollo ADB su TCP (lo stesso usato da "adb connect").
 * Si collega all'adbd dell'auto (di solito 127.0.0.1:5555) e può eseguire comandi shell
 * con i permessi dell'utente "shell" (am force-stop, ps, settings, getevent...).
 */
public final class AdbClient implements Closeable {

    private static final int A_CNXN = 0x4e584e43;
    private static final int A_AUTH = 0x48545541;
    private static final int A_OPEN = 0x4e45504f;
    private static final int A_OKAY = 0x59414b4f;
    private static final int A_CLSE = 0x45534c43;
    private static final int A_WRTE = 0x45545257;

    private static final int A_VERSION = 0x01000000;
    private static final int MAX_PAYLOAD = 256 * 1024;

    private static final int AUTH_TOKEN = 1;
    private static final int AUTH_SIGNATURE = 2;
    private static final int AUTH_RSAPUBLICKEY = 3;

    private static final Charset UTF8 = Charset.forName("UTF-8");

    /** Prefisso DigestInfo SHA-1: adbd firma il token come se fosse già un hash SHA-1. */
    private static final byte[] SHA1_DIGEST_INFO = {
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
    };

    public interface AuthListener {
        /** Chiamato quando l'auto deve mostrare il popup "Consentire il debug?". */
        void onWaitingForApproval();
    }

    public interface LineListener {
        void onLine(String line);
    }

    private static final class Msg {
        int cmd, arg0, arg1;
        byte[] data;
    }

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;
    private int nextLocalId = 1;

    private AdbClient(Socket s) throws IOException {
        socket = s;
        in = new DataInputStream(s.getInputStream());
        out = s.getOutputStream();
    }

    public static AdbClient connect(Context ctx, String host, int port, AuthListener listener) throws IOException {
        Socket s = new Socket();
        s.setTcpNoDelay(true);
        s.connect(new InetSocketAddress(host, port), 4000);
        s.setSoTimeout(10000);
        AdbClient c = new AdbClient(s);
        try {
            c.handshake(ctx, listener);
        } catch (IOException e) {
            c.close();
            throw e;
        }
        s.setSoTimeout(15000);
        return c;
    }

    private void handshake(Context ctx, AuthListener listener) throws IOException {
        send(A_CNXN, A_VERSION, MAX_PAYLOAD, "host::mg4taskmanager\0".getBytes(UTF8));
        KeyPair kp = null;
        boolean sentSignature = false, sentPublicKey = false;
        while (true) {
            Msg m = read();
            if (m.cmd == A_CNXN) return;
            if (m.cmd != A_AUTH || m.arg0 != AUTH_TOKEN) continue;
            if (kp == null) kp = loadOrCreateKey(ctx);
            if (!sentSignature) {
                send(A_AUTH, AUTH_SIGNATURE, 0, sign(kp.getPrivate(), m.data));
                sentSignature = true;
            } else if (!sentPublicKey) {
                send(A_AUTH, AUTH_RSAPUBLICKEY, 0, publicKeyBlob((RSAPublicKey) kp.getPublic()));
                sentPublicKey = true;
                if (listener != null) listener.onWaitingForApproval();
                socket.setSoTimeout(120000); // tempo per toccare "Consenti" sullo schermo
            } else {
                throw new IOException("Autorizzazione ADB rifiutata");
            }
        }
    }

    /** Esegue un comando shell e restituisce tutto l'output. */
    public synchronized String shell(String cmd) throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        run(cmd, null, bos);
        return new String(bos.toByteArray(), UTF8);
    }

    /** Esegue un comando che non termina (es. getevent) passando l'output riga per riga. */
    public void shellStream(String cmd, LineListener l) throws IOException {
        socket.setSoTimeout(0);
        run(cmd, l, null);
    }

    private void run(String cmd, LineListener lines, ByteArrayOutputStream sink) throws IOException {
        int local = nextLocalId++;
        int remote = 0;
        StringBuilder partial = new StringBuilder();
        send(A_OPEN, local, 0, ("shell:" + cmd + "\0").getBytes(UTF8));
        while (true) {
            Msg m = read();
            if (m.arg1 != local) continue;
            if (m.cmd == A_OKAY) {
                remote = m.arg0;
            } else if (m.cmd == A_WRTE) {
                remote = m.arg0;
                if (sink != null) sink.write(m.data, 0, m.data.length);
                if (lines != null) {
                    partial.append(new String(m.data, UTF8));
                    int nl;
                    while ((nl = partial.indexOf("\n")) >= 0) {
                        String line = partial.substring(0, nl).replace("\r", "");
                        partial.delete(0, nl + 1);
                        lines.onLine(line);
                    }
                }
                send(A_OKAY, local, remote, null);
            } else if (m.cmd == A_CLSE) {
                if (remote != 0) send(A_CLSE, local, remote, null);
                return;
            }
        }
    }

    private void send(int cmd, int arg0, int arg1, byte[] data) throws IOException {
        int len = data == null ? 0 : data.length;
        int sum = 0;
        for (int i = 0; i < len; i++) sum += data[i] & 0xff;
        ByteBuffer b = ByteBuffer.allocate(24 + len).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(cmd).putInt(arg0).putInt(arg1).putInt(len).putInt(sum).putInt(~cmd);
        if (len > 0) b.put(data);
        out.write(b.array());
        out.flush();
    }

    private Msg read() throws IOException {
        byte[] h = new byte[24];
        in.readFully(h);
        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        Msg m = new Msg();
        m.cmd = b.getInt();
        m.arg0 = b.getInt();
        m.arg1 = b.getInt();
        int len = b.getInt();
        b.getInt(); // checksum
        int magic = b.getInt();
        if (magic != ~m.cmd) throw new IOException("Risposta ADB non valida");
        if (len < 0 || len > 16 * 1024 * 1024) throw new IOException("Pacchetto ADB troppo grande");
        m.data = new byte[len];
        in.readFully(m.data);
        return m;
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    // ---------------------------------------------------------------- chiavi RSA

    private static synchronized KeyPair loadOrCreateKey(Context ctx) throws IOException {
        File priv = new File(ctx.getFilesDir(), "adbkey");
        File pub = new File(ctx.getFilesDir(), "adbkey.pub");
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            if (priv.exists() && pub.exists()) {
                PrivateKey pk = kf.generatePrivate(new PKCS8EncodedKeySpec(readFile(priv)));
                RSAPublicKey pubk = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(readFile(pub)));
                return new KeyPair(pubk, pk);
            }
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KeyPair kp = g.generateKeyPair();
            writeFile(priv, kp.getPrivate().getEncoded());
            writeFile(pub, kp.getPublic().getEncoded());
            return kp;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Chiave RSA: " + e.getMessage());
        }
    }

    private static byte[] sign(PrivateKey key, byte[] token) throws IOException {
        try {
            byte[] em = new byte[256];
            int ffEnd = 256 - SHA1_DIGEST_INFO.length - token.length - 1;
            em[0] = 0;
            em[1] = 1;
            for (int i = 2; i < ffEnd; i++) em[i] = (byte) 0xff;
            em[ffEnd] = 0;
            System.arraycopy(SHA1_DIGEST_INFO, 0, em, ffEnd + 1, SHA1_DIGEST_INFO.length);
            System.arraycopy(token, 0, em, ffEnd + 1 + SHA1_DIGEST_INFO.length, token.length);
            Cipher c = Cipher.getInstance("RSA/ECB/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key);
            return c.doFinal(em);
        } catch (Exception e) {
            throw new IOException("Firma ADB: " + e.getMessage());
        }
    }

    /** Chiave pubblica nel formato di adb (struttura RSAPublicKey di mincrypt in base64). */
    private static byte[] publicKeyBlob(RSAPublicKey k) {
        BigInteger n = k.getModulus();
        BigInteger r32 = BigInteger.ZERO.setBit(32);
        BigInteger n0inv = r32.subtract(n.mod(r32).modInverse(r32));
        BigInteger rr = BigInteger.ZERO.setBit(2048).modPow(BigInteger.valueOf(2), n);
        ByteBuffer b = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(64);
        b.putInt(n0inv.intValue());
        BigInteger t = n;
        for (int i = 0; i < 64; i++) {
            b.putInt(t.mod(r32).intValue());
            t = t.shiftRight(32);
        }
        t = rr;
        for (int i = 0; i < 64; i++) {
            b.putInt(t.mod(r32).intValue());
            t = t.shiftRight(32);
        }
        b.putInt(k.getPublicExponent().intValue());
        String s = Base64.encodeToString(b.array(), Base64.NO_WRAP) + " mg4taskmanager@car\0";
        return s.getBytes(UTF8);
    }

    private static byte[] readFile(File f) throws IOException {
        FileInputStream is = new FileInputStream(f);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = is.read(buf)) > 0) bos.write(buf, 0, r);
            return bos.toByteArray();
        } finally {
            is.close();
        }
    }

    private static void writeFile(File f, byte[] d) throws IOException {
        FileOutputStream os = new FileOutputStream(f);
        try {
            os.write(d);
        } finally {
            os.close();
        }
    }
}
