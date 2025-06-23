package com.aws.imageapp.service;

import com.aws.imageapp.dto.ImageData;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class S3Service {
    private final S3Client s3Client;
    
    @Value( "${aws.s3.bucket}" )
    private String bucketName;
    
    @Value( "${aws.region}" )
    private String region;
    
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
     * Lists image URLs from the specified S3 bucket, filtered by the search term if provided and
     * limited to the specified page size.
     *
     * @param page the page number to retrieve (1-indexed)
     * @param size the number of image URLs to retrieve
     * @param search the search term to filter image URLs by (case-insensitive)
     * @return a list of ImageData objects containing the image URL and file name
     */
    public List<ImageData> listImageUrls(int page, int size, String search) {
        List<S3Object> allFilteredResults = new ArrayList<>();
        String continuationToken = null;
        
        // Fetch all matching results first
        do {
            ListObjectsV2Request.Builder reqBuilder = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .maxKeys(1000);
            
            if (continuationToken != null) {
                reqBuilder.continuationToken(continuationToken);
            }
            
            ListObjectsV2Response response = s3Client.listObjectsV2(reqBuilder.build());
            
            List<S3Object> matchingObjects = (search == null || search.isBlank())
                    ? response.contents()
                    : response.contents().stream()
                    .filter(obj -> obj.key().toLowerCase().contains(search.toLowerCase()))
                    .toList();
            
            allFilteredResults.addAll(matchingObjects);
            
            continuationToken = response.nextContinuationToken();
        } while (continuationToken != null);
        
        // Now paginate the results
        int fromIndex = Math.min((page - 1) * size, allFilteredResults.size());
        int toIndex = Math.min(fromIndex + size, allFilteredResults.size());
        
        return allFilteredResults.subList(fromIndex, toIndex).stream()
                .map(obj -> {
                    String key = obj.key();
                    String url = String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, key);
                    String fileName = key.contains("/") ? key.substring(key.lastIndexOf("/") + 1) : key;
                    return new ImageData(url, fileName);
                })
                .toList();
    }
    
    /**
     * Calculates the total number of pages required to display all the images in the S3 bucket,
     * filtered by the search term if provided, and limited to the specified page size.
     *
     * @param size   the number of images per page
     * @param search the search term to filter images by (case-insensitive)
     * @return the total number of pages required to display all the filtered images
     */
    public int getTotalPages(int size, String search) {
        int count = 0;
        String continuationToken = null;
        
        do {
            ListObjectsV2Request.Builder reqBuilder = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .maxKeys(1000);
            
            if (continuationToken != null) {
                reqBuilder.continuationToken(continuationToken);
            }
            
            ListObjectsV2Response response = s3Client.listObjectsV2(reqBuilder.build());
            
            List<S3Object> matching = (search == null || search.isBlank())
                    ? response.contents()
                    : response.contents().stream()
                    .filter(obj -> obj.key().toLowerCase().contains(search.toLowerCase()))
                    .toList();
            
            count += matching.size();
            continuationToken = response.nextContinuationToken();
        } while (continuationToken != null);
        
        return (int) Math.ceil((double) count / size);
    }
    
    public void delete (String key) {
        s3Client.deleteObject( DeleteObjectRequest.builder()
                .bucket( bucketName )
                .key( key )
                .build() );
    }
}
