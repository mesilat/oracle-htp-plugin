package com.mesilat.ora;

public class MakeFileDownloadException extends Exception {
    private final String fileName;
    private final byte[] data;

    public String getFileName() {
        return fileName;
    }
    public byte[] getData() {
        return data;
    }

    public MakeFileDownloadException(String fileName, byte[] data){
        this.fileName = fileName;
        this.data = data;
    }
}