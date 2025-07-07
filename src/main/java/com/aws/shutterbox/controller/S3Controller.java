package com.aws.shutterbox.controller;

import com.aws.shutterbox.entity.dto.ImageData;
import com.aws.shutterbox.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Controller
@RequiredArgsConstructor
@Slf4j
public class S3Controller {
    private final S3Service s3Service;
    
    /**
     * GET endpoint for the homepage, which displays a list of images stored in the S3 bucket.
     *
     * @param page the page number to retrieve (1-indexed)
     * @param size the number of images per page
     * @param search the search term to filter images by
     * @param model the Spring MVC Model that will be populated with data
     * @return the name of the Thymeleaf template to render
     */
    @GetMapping("/")
    public String home(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(required = false) String search,
                       Model model) {
        
        List<ImageData> filtered = s3Service.listImageUrls(page, size, search);
        int totalPages = s3Service.getTotalPages(size, search);
        
        // Generate numbered page list
        List<Integer> pageNumbers = IntStream.rangeClosed(1, totalPages)
                .boxed()
                .collect( Collectors.toList());
        
        // Add attributes
        model.addAttribute("images", filtered);
        model.addAttribute("size", size);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", pageNumbers);
        model.addAttribute("search", search);
        
        return "index";
    }
    
    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam("description") String description) {
        try {
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new IllegalArgumentException("Only image files are allowed.");
            }
            
            s3Service.upload(file, description);
            return ResponseEntity.ok("File uploaded successfully!");
        } catch (Exception e) {
            log.error("Upload failed", e);
            return ResponseEntity.badRequest().body("Upload failed: " + e.getMessage());
        }
    }
    
    /**
     * Deletes an image from S3 and redirects back to the homepage.
     *
     * @param key the S3 key of the image to delete
     * @param redirectAttributes the redirect attributes to add success/error messages to
     * @return "redirect:/" to redirect back to the homepage
     */
    @PostMapping("/delete")
    public String deleteImage(@RequestParam("key") String key, RedirectAttributes redirectAttributes) {
        try {
            s3Service.delete(key);
            redirectAttributes.addFlashAttribute("message", "Deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Deletion failed: " + e.getMessage());
        }
        return "redirect:/";
    }
}
