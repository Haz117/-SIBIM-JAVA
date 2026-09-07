package com.sibim.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupabaseStorageTest {

    @Test
    void isRemoteUrl_null_returnsFalse() {
        assertFalse(SupabaseStorage.isRemoteUrl(null));
    }

    @Test
    void isRemoteUrl_empty_returnsFalse() {
        assertFalse(SupabaseStorage.isRemoteUrl(""));
    }

    @Test
    void isRemoteUrl_windowsLocalPath_returnsFalse() {
        assertFalse(SupabaseStorage.isRemoteUrl("C:\\Users\\TI\\.sibim\\imagenes\\abc123_0.jpg"));
    }

    @Test
    void isRemoteUrl_unixLocalPath_returnsFalse() {
        assertFalse(SupabaseStorage.isRemoteUrl("/home/usuario/.sibim/imagenes/abc123_0.jpg"));
    }

    @Test
    void isRemoteUrl_relativePathWithoutScheme_returnsFalse() {
        assertFalse(SupabaseStorage.isRemoteUrl("imagenes/abc123_0.jpg"));
    }

    @Test
    void isRemoteUrl_httpUrl_returnsTrue() {
        assertTrue(SupabaseStorage.isRemoteUrl("http://example.com/foto.jpg"));
    }

    @Test
    void isRemoteUrl_httpsSupabaseStorageUrl_returnsTrue() {
        assertTrue(SupabaseStorage.isRemoteUrl(
            "https://zjrzrcfpvkkefsscvzyh.supabase.co/storage/v1/object/public/sibim-fotos/abc123_0.jpg"));
    }
}
