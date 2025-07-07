package com.aws.shutterbox.service;

import com.aws.shutterbox.entity.ImageMetaData;
import com.aws.shutterbox.entity.dto.ImageData;
import com.aws.shutterbox.repository.ImageMetaDataRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class S3Service {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ImageMetaDataRepository imageRepo;
    
    @Value( "${aws.s3.bucket}" )
    private String bucketName;
    
    @Value( "${aws.region}" )
    private String region;
    
    
    /**
     * Uploads a file to an S3 bucket and saves its metadata to a database.
     *
     * @param file the file to upload
     * @param description a description of the file
     * @throws IOException if there is an error reading the file
     */
    public void upload (MultipartFile file, String description) throws IOException {
        String key = file.getOriginalFilename();
        String contentType = file.getContentType();
        long fileSize = file.getSize();
        
        //Validate file name
        if ( key != null && !key.isBlank() ) {
            imageRepo.findByKey( key ).ifPresent( image -> {
                throw new IllegalArgumentException( "File name already exists. Rename the file and try again" );
            } );
        }
        // Upload to S3
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket( bucketName )
                .key( key )
                .contentLength( fileSize )
                .contentType( contentType )
                .build();
        
        s3Client.putObject( putRequest, RequestBody.fromBytes( file.getBytes() ) );
        
        // Save metadata to DB
        String url = String.format( "https://%s.s3.%s.amazonaws.com/%s", bucketName, region, key );
        
        ImageMetaData metadata = ImageMetaData.builder()
                .key( key )
                .fileName( key )
                .contentType( contentType )
                .size( fileSize )
                .url( url )
                .description( description == null ? "" : description )
                .build();
        
        imageRepo.save( metadata );
    }
    
    /**
     * List image URLs and metadata in the S3 bucket.
     *
     * @param page the page number to retrieve (1-indexed)
     * @param size the number of images per page
     * @param search the search term to filter images by
     * @return a list of {@link ImageData} containing the presigned URL and metadata
     */
    public List<ImageData> listImageUrls (int page, int size, String search) {
        Pageable pageable = PageRequest.of( page - 1, size );
        
        Page<ImageMetaData> pageResult = ( search != null && !search.isBlank() )
                ? imageRepo.searchByFilenameOrDescription( search, pageable )
                : imageRepo.findAllByOrderByUploadTimestampDesc( pageable );
        
        return pageResult.stream()
                .map( meta -> {
                    String presignedGetUrl = createPresignedGetUrl( meta.getKey() );
                    return new ImageData( presignedGetUrl, meta.getFileName(), meta.getKey(), meta.getDescription() );
                } )
                .toList();
    }
    
    /**
     * Creates a presigned URL for accessing an object in the S3 bucket.
     * <p>
     * This method generates a presigned URL that allows temporary read access
     * to a specific object in the S3 bucket, identified by its key. The URL
     * is valid for 10 minutes and can be used to retrieve the object without
     * requiring AWS credentials.
     *
     * @param key the S3 key of the object to create a presigned URL for
     * @return a presigned URL as a String for accessing the object
     */
    private String createPresignedGetUrl (String key) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration( Duration.ofMinutes( 10 ) )
                .getObjectRequest( b -> b
                        .bucket( bucketName )
                        .key( key ) )
                .build();
        
        return s3Presigner.presignGetObject( presignRequest ).url().toString();
    }
    
    /**
     * Returns the total number of pages for the given page size and search term.
     *
     * @param size the number of items per page
     * @param search the search term to filter images by
     * @return the total number of pages
     */
    public int getTotalPages (int size, String search) {
        long count = ( search != null && !search.isBlank() )
                ? imageRepo.countByFilenameOrDescription( search )
                : imageRepo.count();
        
        return (int) Math.ceil( (double) count / size );
    }
    
    /**
     * Deletes an image from the S3 bucket and its metadata from the database.
     *
     * <p>This method removes an object identified by its S3 key from the specified
     * bucket and deletes the corresponding metadata record from the database if it exists.</p>
     *
     * @param key the S3 key of the image to be deleted
     */
    public void delete (String key) {
        s3Client.deleteObject( DeleteObjectRequest.builder()
                .bucket( bucketName )
                .key( key )
                .build() );
        
        // Delete from DB
        imageRepo.findByKey( key ).ifPresent( imageRepo::delete );
    }
}
