package com.lyj.securitydomo.controller;

import com.lyj.securitydomo.domain.QPost;
import com.lyj.securitydomo.domain.pPhoto;
import com.lyj.securitydomo.dto.PageRequestDTO;
import com.lyj.securitydomo.dto.PageResponseDTO;
import com.lyj.securitydomo.dto.PostDTO;
import com.lyj.securitydomo.dto.upload.UploadFileDTO;
import com.lyj.securitydomo.service.PostService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnailator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;

@Controller
@Log4j2
@RequestMapping("/posting")
@RequiredArgsConstructor
public class PostController {

    @Value("${com.lyj.securitydomo.upload.path}")
    private String uploadPath;

    private final PostService postService;

    /**
     * 게시글 목록을 조회하고 모델에 전달하는 메서드
     *
     * @return
     */
    @GetMapping("/list")
    public String list(PageRequestDTO pageRequestDTO, Model model) {
        // size가 유효하지 않은 경우 기본값으로 설정
        if (pageRequestDTO.getSize() <= 0) {
            pageRequestDTO.setSize(10); // 기본값 설정
        }

        // 게시글 목록을 가져올 때, isVisible이 true인 게시글만 필터링
        PageResponseDTO<PostDTO> responseDTO = postService.list(pageRequestDTO);

        // 모델에 게시글을 추가하기 전에 로그 출력
        log.info("게시글 목록 전달: {}", responseDTO.getDtoList());

        model.addAttribute("posts", responseDTO.getDtoList()); // 게시글 DTO 리스트 추가
        model.addAttribute("totalPages", (int) Math.ceil(responseDTO.getTotal() / (double) pageRequestDTO.getSize())); // 총 페이지 수 계산
        model.addAttribute("currentPage", responseDTO.getPage()); // 현재 페이지 추가

        return "posting/list"; // 게시글 목록 뷰 반환
    }


    /**
     * 게시글 등록 페이지를 보여주는 메서드
     */
    @GetMapping("/register")
    public void registerGET() {
    }

    /**
     * 게시글 등록을 처리하는 메서드
     */
    @PostMapping("/register")
    public String registerPost(UploadFileDTO uploadFileDTO, PostDTO postDTO, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        try {
            List<String> fileNames = new ArrayList<>();
            if (uploadFileDTO.getFiles() != null && uploadFileDTO.getFiles().size() > 0) {
                // 파일이 있을 경우에만 업로드
                fileNames = uploadFiles(uploadFileDTO);
            } else {
                // 파일이 없으면 랜덤 이미지 사용
                String randomImage = pPhoto.getRandomImage(); // pPhoto에서 랜덤 이미지 가져오기
                fileNames.add(randomImage); // 랜덤 이미지를 fileNames 리스트에 추가
                log.info("파일이 업로드되지 않았습니다. 랜덤 이미지를 사용합니다: {}", randomImage); // 로그 추가
            }

            postDTO.setFileNames(fileNames); // 파일 이름 설정

            Long postId = postService.register(postDTO); // 게시글 등록

            if (postId == null) {
                redirectAttributes.addFlashAttribute("error", "게시글 등록에 실패했습니다."); // 등록 실패 알림
                return "redirect:/posting/register"; // 등록 페이지로 리디렉션
            }

            redirectAttributes.addFlashAttribute("result", postId); // 성공적으로 등록된 ID
            log.info("게시글이 성공적으로 등록되었습니다. ID: {}", postId); // 로그 추가
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "이미지 파일만 업로드 가능합니다."); // 오류 알림
            return "redirect:/posting/register"; // 등록 페이지로 리디렉션
        }
        return "redirect:/posting/list"; // 리스트 페이지로 리디렉션
    }

    /**
     * 게시글 읽기 및 수정 페이지를 보여주는 메서드
     */
    @GetMapping("/read/{postId}")
    public String read(@PathVariable Long postId, Model model) {
        PostDTO postDTO = postService.readOne(postId);
        log.info(postDTO);
        model.addAttribute("post", postDTO);
        model.addAttribute("originalImages", postDTO.getOriginalImageLinks()); // 이미지 링크 추가
return "posting/read";
    }
    @GetMapping("/modify/{postId}")
    public String modify(@PathVariable Long postId, Model model) {
        PostDTO postDTO = postService.readOne(postId);
        log.info(postDTO);
        model.addAttribute("post", postDTO);
        model.addAttribute("originalImages", postDTO.getOriginalImageLinks()); // 이미지 링크 추가
        return "posting/modify";
    }


    /**
     * 게시글 수정 처리를 위한 메서드
     */
    @PostMapping("/modify/{postId}")
    public String modify(PageRequestDTO pageRequestDTO,
                         UploadFileDTO uploadFileDTO,
                         @Valid PostDTO postDTO,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        log.info("board modify post......." + postDTO);
        List<String> strFileNames = null;

        if (uploadFileDTO.getFiles() != null &&
                !uploadFileDTO.getFiles().get(0).getOriginalFilename().equals("")) {

            List<String> fileNames = postDTO.getFileNames();

            if (fileNames != null && fileNames.size() > 0) {
                removeFile(fileNames);
            }

            strFileNames = fileUpload(uploadFileDTO);
            log.info("!!!!!!!!!!!!!!!!" + strFileNames.size());
            postDTO.setFileNames(strFileNames);
        }

        if (bindingResult.hasErrors()) {
            log.info("has errors.......");
            String link = pageRequestDTO.getLink();
            redirectAttributes.addFlashAttribute("errors", bindingResult.getAllErrors());
            redirectAttributes.addAttribute("postId", postDTO.getPostId());
            return "redirect:/posting/modify?" + link;
        }

        postService.modify(postDTO);
        redirectAttributes.addFlashAttribute("result", "modified");

        // 수정된 게시글의 postId를 포함하여 특정 게시글로 리디렉션
        redirectAttributes.addAttribute("postId", postDTO.getPostId());
        return "redirect:/posting/read/{postId}";
    }


    @GetMapping("/remove/{postId}")
    public String remove(@PathVariable Long postId, RedirectAttributes redirectAttributes) {
        log.info("Removing post with ID: " + postId);

        // 파일 및 게시글 삭제 서비스 호출

        postService.remove(postId);

        redirectAttributes.addFlashAttribute("result", "removed");
        return "redirect:/posting/list";
    }


    private List<String> fileUpload(UploadFileDTO uploadFileDTO) {

        List<String> list = new ArrayList<>();
        uploadFileDTO.getFiles().forEach(multipartFile -> {
            String originalName = multipartFile.getOriginalFilename();
            log.info(originalName);

            String uuid = UUID.randomUUID().toString();
            Path savePath = Paths.get(uploadPath, uuid + "_" + originalName);
            boolean image = false;
            try {
                multipartFile.transferTo(savePath); // 서버에 파일저장
                //이미지 파일의 종류라면
                if (Files.probeContentType(savePath).startsWith("image")) {
                    image = true;
                    File thumbFile = new File(uploadPath, "s_" + uuid + "_" + originalName);
                    Thumbnailator.createThumbnail(savePath.toFile(), thumbFile, 200, 200);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            list.add(uuid + "_" + originalName);
        });
        return list;
    }

    /**
     * 파일 업로드 처리 메서드
     *
     * @param uploadFileDTO 업로드할 파일 정보
     * @return 파일 이름 리스트
     */


    private List<String> uploadFiles(UploadFileDTO uploadFileDTO) throws IOException {
        List<String> fileNames = new ArrayList<>();

        if (uploadFileDTO.getFiles() != null && uploadFileDTO.getFiles().size() > 0) {
            for (MultipartFile file : uploadFileDTO.getFiles()) {
                String originalName = file.getOriginalFilename();
                String uuid = UUID.randomUUID().toString();
                Path savePath = Paths.get(uploadPath, uuid + "_" + originalName);

                // 파일이 비어있지 않은지 확인
                if (file.isEmpty()) {
                    // 비어있는 경우 랜덤 이미지를 추가
                    String randomImage = pPhoto.getRandomImage();
                    fileNames.add(randomImage); // 랜덤 이미지를 파일 이름에 추가
                    continue; // 다음 파일로 넘어감
                }

                // 파일을 지정된 경로에 저장
                file.transferTo(savePath);

                // 이미지 파일인지 확인
                String contentType = Files.probeContentType(savePath);
                if (contentType != null && contentType.startsWith("image")) {
                    // 이미지 파일일 경우 썸네일 생성
                    File thumbnailFile = new File(uploadPath, "s_" + uuid + "_" + originalName);
                    Thumbnailator.createThumbnail(savePath.toFile(), thumbnailFile, 200, 200);
                    fileNames.add("s_" + uuid + "_" + originalName); // 썸네일 이름 추가
                    fileNames.add(uuid + "_" + originalName); // 원본 파일 이름 추가
                } else {
                    // 이미지 파일이 아닐 경우 예외 발생
                    throw new IOException("Uploaded file is not an image: " + originalName);
                }
            }
        } else {
            // 파일이 아예 없을 경우 랜덤 이미지를 추가
            String randomImage = pPhoto.getRandomImage();
            fileNames.add(randomImage); // 랜덤 이미지를 파일 이름에 추가
        }
        return fileNames;
    }

    /**
     * 파일을 뷰로 보여주는 메서드
     *
     * @param fileName 파일 이름
     * @return 파일에 대한 ResponseEntity
     */
    @GetMapping("/view/{fileName}")
    @ResponseBody
    public ResponseEntity<Resource> viewFile(@PathVariable("fileName") String fileName) {
        Resource resource = new FileSystemResource(uploadPath + File.separator + fileName);
        HttpHeaders headers = new HttpHeaders();

        try {
            headers.add("Content-Type", Files.probeContentType(resource.getFile().toPath()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
        return ResponseEntity.ok().headers(headers).body(resource);
    }

    private void removeFile(List<String> fileNames) {
        log.info("AAAAA" + fileNames.size());

        for (String fileName : fileNames) {
            log.info("fileRemove method: " + fileName);
            Resource resource = new FileSystemResource(uploadPath + File.separator + fileName);
            String resourceName = resource.getFilename();

            // Map<String, Boolean> resultMap = new HashMap<>();
            boolean removed = false;

            try {
                String contentType = Files.probeContentType(resource.getFile().toPath());
                removed = resource.getFile().delete();

                //섬네일이 존재한다면
                if (contentType.startsWith("image")) {
                    String fileName1 = fileName.replace("s_", "");
                    File originalFile = new File(uploadPath + File.separator + fileName1);
                    originalFile.delete();
                }

            } catch (Exception e) {
                log.error(e.getMessage());


                /**
                 * 신고 처리 및 비공개 처리 메서드
                 * 관리자가 신고된 게시글을 비공개로 처리
                 */
//                // 게시글 비공개 처리
//                @PostMapping("/report/{postId}/process")
//                public String markPostAsInvisible (@PathVariable Long postId, RedirectAttributes redirectAttributes){
//                    try {
//                        postService.markPostAsInvisible(postId); // 비공개 처리 서비스 호출
//                        redirectAttributes.addFlashAttribute("message", "게시글이 비공개 처리되었습니다.");
//                        return "redirect:/posting/list"; // 리스트 페이지로 리디렉션
//                  } catch (EntityNotFoundException e) {
//                redirectAttributes.addFlashAttribute("error", "해당 게시글을 찾을 수 없습니다.");
//            }
        }
    }
 }
}
