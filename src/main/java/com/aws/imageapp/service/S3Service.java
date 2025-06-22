package com.aws.imageapp.service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class S3Service {
    private final S3Client s3Client;
    
    @Value( "${aws.s3.bucket}" )
    private String bucketName;
    
    /**
     * Uploads a file to the specified S3 bucket with public read access.
     *
     * @param file the MultipartFile to upload
     * @throws IOException if an I/O error occurs during file processing
     */
    public void upload (MultipartFile file) throws IOException {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket( bucketName )
                .key( file.getOriginalFilename() )
                .contentType( file.getContentType() )
                .build();
        
        s3Client.putObject( putRequest, RequestBody.fromBytes( file.getBytes() ) );
    }
    
    /**
     * Returns a list of publicly accessible URLs for the images in the specified S3 bucket,
     * filtered by the search term if provided, and limited to the specified page and size.
     *
     * @param page the page number to retrieve (1-indexed)
     * @param size the number of images per page
     * @param search the search term to filter images by (case-insensitive)
     * @return a list of publicly accessible URLs for the filtered images
     */
    public List<String> listImageUrls(int page, int size, String search) {
        ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build());
        
        List<S3Object> allObjects = response.contents();
        
        // Filter by search if provided
        List<S3Object> filtered = (search == null || search.isBlank())
                ? allObjects
                : allObjects.stream()
                .filter(obj -> obj.key().toLowerCase().contains(search.toLowerCase()))
                .toList();
        
        int from = Math.min((page - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        
        return filtered.subList(from, to).stream()
                .map(obj -> String.format("https://%s.s3.amazonaws.com/%s", bucketName, obj.key()))
                .toList();
    }
    
    /**
     * Calculates the total number of pages of images in the S3 bucket, given the specified page size
     * and search term (if provided).
     *
     * @param size the page size
     * @param search the search term to filter images by (case-insensitive)
     * @return the total number of pages
     */
    public int getTotalPages(int size, String search) {
        ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build());
        
        long total = (search == null || search.isBlank())
                ? response.contents().size()
                : response.contents().stream()
                .filter(obj -> obj.key().toLowerCase().contains(search.toLowerCase()))
                .count();
        
        return (int) Math.ceil((double) total / size);
    }
    
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
    }
}
