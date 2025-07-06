package com.aws.imageapp.service;

import com.aws.imageapp.entity.ImageMetaData;
import com.aws.imageapp.entity.dto.ImageData;
import com.aws.imageapp.repository.ImageMetaDataRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
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
    private final ImageMetaDataRepository imageRepo;
    
    @Value( "${aws.s3.bucket}" )
    private String bucketName;
    
    @Value( "${aws.region}" )
    private String region;
    
    public void upload (MultipartFile file, String description) throws IOException {
        String key = file.getOriginalFilename();
        String contentType = file.getContentType();
        long fileSize = file.getSize();
       
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
    
    public List<ImageData> listImageUrls (int page, int size, String search) {
        Pageable pageable = PageRequest.of( page - 1, size );
        
        Page<ImageMetaData> pageResult = ( search != null && !search.isBlank() )
                ? imageRepo.searchByFilenameOrDescription( search, pageable )
                : imageRepo.findAllByOrderByUploadTimestampDesc( pageable );
        
        return pageResult.stream()
                .map( meta -> new ImageData( meta.getUrl(), meta.getFileName(), meta.getKey(), meta.getDescription() ) )
                .toList();
    }
    
    public int getTotalPages (int size, String search) {
        long count = ( search != null && !search.isBlank() )
                ? imageRepo.countByFilenameOrDescription( search )
                : imageRepo.count();
        
        return (int) Math.ceil( (double) count / size );
    }
    
    public void delete (String key) {
        s3Client.deleteObject( DeleteObjectRequest.builder()
                .bucket( bucketName )
                .key( key )
                .build() );
        
        // Delete from DB
        imageRepo.findByKey( key ).ifPresent( imageRepo::delete );
    }
}
