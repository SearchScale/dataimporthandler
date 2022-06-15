package org.apache.solr.handler.dataimport;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

import org.apache.solr.common.SolrException;

/**
 * Code copied from Solr 8.11.1
 */
public class CryptoKeys {

    public static String decodeAES(String base64CipherTxt, String pwd) {
        int[] strengths = new int[]{256, 192, 128};
        Exception e = null;
        for (int strength : strengths) {
            try {
                return decodeAES(base64CipherTxt, pwd, strength);
            } catch (Exception exp) {
                e = exp;
            }
        }
        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Error decoding ", e);
    }

    /**
     * Code copied from a 2019 Stack Overflow post by Maarten Bodewes
     * https://stackoverflow.com/questions/11783062/how-to-decrypt-file-in-java-encrypted-with-openssl-command-using-aes
     */
    public static String decodeAES(String base64CipherTxt, String pwd, final int keySizeBits) {
        final Charset ASCII = Charset.forName("ASCII");
        final int INDEX_KEY = 0;
        final int INDEX_IV = 1;
        final int ITERATIONS = 1;
        final int SALT_OFFSET = 8;
        final int SALT_SIZE = 8;
        final int CIPHERTEXT_OFFSET = SALT_OFFSET + SALT_SIZE;

        try {
            byte[] headerSaltAndCipherText = Base64.getDecoder().decode(base64CipherTxt);

            // --- extract salt & encrypted ---
            // header is "Salted__", ASCII encoded, if salt is being used (the default)
            byte[] salt = Arrays.copyOfRange(
                    headerSaltAndCipherText, SALT_OFFSET, SALT_OFFSET + SALT_SIZE);
            byte[] encrypted = Arrays.copyOfRange(
                    headerSaltAndCipherText, CIPHERTEXT_OFFSET, headerSaltAndCipherText.length);

            // --- specify cipher and digest for evpBytesTokey method ---

            Cipher aesCBC = Cipher.getInstance("AES/CBC/PKCS5Padding");
            MessageDigest md5 = MessageDigest.getInstance("MD5");

            // --- create key and IV  ---

            // the IV is useless, OpenSSL might as well have use zero's
            final byte[][] keyAndIV = evpBytesTokey(
                    keySizeBits / Byte.SIZE,
                    aesCBC.getBlockSize(),
                    md5,
                    salt,
                    pwd.getBytes(ASCII),
                    ITERATIONS);

            SecretKeySpec key = new SecretKeySpec(keyAndIV[INDEX_KEY], "AES");
            IvParameterSpec iv = new IvParameterSpec(keyAndIV[INDEX_IV]);

            // --- initialize cipher instance and decrypt ---

            aesCBC.init(Cipher.DECRYPT_MODE, key, iv);
            byte[] decrypted = aesCBC.doFinal(encrypted);
            return new String(decrypted, ASCII);
        } catch (BadPaddingException e) {
            // AKA "something went wrong"
            throw new IllegalStateException(
                    "Bad password, algorithm, mode or padding;" +
                            " no salt, wrong number of iterations or corrupted ciphertext.", e);
        } catch (IllegalBlockSizeException e) {
            throw new IllegalStateException(
                    "Bad algorithm, mode or corrupted (resized) ciphertext.", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Method copied from blog post https://olabini.se/blog/2006/10/openssl-in-jruby/
     * where it is released into the Public Domain, also see LICENSE.txt
     */
    private static byte[][] evpBytesTokey(int key_len, int iv_len, MessageDigest md,
                                          byte[] salt, byte[] data, int count) {
        byte[][] both = new byte[2][];
        byte[] key = new byte[key_len];
        int key_ix = 0;
        byte[] iv = new byte[iv_len];
        int iv_ix = 0;
        both[0] = key;
        both[1] = iv;
        byte[] md_buf = null;
        int nkey = key_len;
        int niv = iv_len;
        int i = 0;
        if (data == null) {
            return both;
        }
        int addmd = 0;
        for (; ; ) {
            md.reset();
            if (addmd++ > 0) {
                md.update(md_buf);
            }
            md.update(data);
            if (null != salt) {
                md.update(salt, 0, 8);
            }
            md_buf = md.digest();
            for (i = 1; i < count; i++) {
                md.reset();
                md.update(md_buf);
                md_buf = md.digest();
            }
            i = 0;
            if (nkey > 0) {
                for (; ; ) {
                    if (nkey == 0)
                        break;
                    if (i == md_buf.length)
                        break;
                    key[key_ix++] = md_buf[i];
                    nkey--;
                    i++;
                }
            }
            if (niv > 0 && i != md_buf.length) {
                for (; ; ) {
                    if (niv == 0)
                        break;
                    if (i == md_buf.length)
                        break;
                    iv[iv_ix++] = md_buf[i];
                    niv--;
                    i++;
                }
            }
            if (nkey == 0 && niv == 0) {
                break;
            }
        }
        for (i = 0; i < md_buf.length; i++) {
            md_buf[i] = 0;
        }
        return both;
    }

}
