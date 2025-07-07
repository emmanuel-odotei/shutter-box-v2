package com.aws.shutterbox.configs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class AWSConfig {
    
    /**
     * Configures and provides an S3Client bean.
     * <p>
     * This method creates and returns an S3Client instance configured with the specified AWS region
     * and default credentials provider. The S3Client is a thread-safe, reusable object that allows
     * interaction with Amazon S3.
     *
     * @return a configured S3Client instance
     */
    @Bean
    public S3Client s3Client (@Value( "${aws.region}" ) String region) {
        return S3Client.builder()
                .region( Region.of( region ) )
                .credentialsProvider( DefaultCredentialsProvider.create() )
                .build();
    }
    
    /**
     * Provides an S3Presigner bean.
     * <p>
     * This method creates and returns an S3Presigner instance that can be used to generate presigned URLs
     * for accessing objects in an S3 bucket. The S3Presigner is a thread-safe, reusable object that can be
     * used to generate URLs for retrieving objects, list objects, and other operations.
     *
     * @return an S3Presigner instance
     */
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.create();
    }
}
