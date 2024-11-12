
package com.lyj.securitydomo.service;

import com.lyj.securitydomo.domain.Post;
import com.lyj.securitydomo.domain.pPhoto;
import com.lyj.securitydomo.dto.PageRequestDTO;
import com.lyj.securitydomo.dto.PageResponseDTO;
import com.lyj.securitydomo.dto.PostDTO;
import com.lyj.securitydomo.repository.PostRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Log4j2
@Transactional
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {
    @Value("${com.lyj.securitydomo.upload.path}")
    private String uploadPath;
    @Override
    public Long register(PostDTO postDTO) {
        // 1. Post 엔티티 생성
        Post post = Post.builder()
                .title(postDTO.getTitle())
                .contentText(postDTO.getContentText())
                .requiredParticipants(postDTO.getRequiredParticipants())
                .status(postDTO.getStatus() != null ? Post.Status.valueOf(postDTO.getStatus()) : null)
                .lat(postDTO.getLat()) // 위도 추가
                .lng(postDTO.getLng()) // 경도 추가
                .build();

        // 2. 파일 정보 추가 (파일이 존재하는 경우에만 추가)
        if (postDTO.getFileNames() != null && !postDTO.getFileNames().isEmpty()) {
            postDTO.getFileNames().forEach(fileName -> {
                String[] split = fileName.split("_");
                if (split.length == 2) {
                    post.addImage(split[0], split[1]);
                }
            });
        } else {
            // 파일이 없을 경우 랜덤 이미지 추가
            String randomImage = pPhoto.getRandomImage(); // pPhoto에서 랜덤 이미지 가져오기
            post.addImage(randomImage, randomImage); // 랜덤 이미지를 파일 이름으로 사용
            log.info("파일이 없어서 랜덤 이미지를 사용합니다: {}", randomImage); // 로그 추가

        }
        // 3. Post 엔티티를 저장 (연관된 pPhoto 엔티티도 함께 저장됨)
        Long postId = postRepository.save(post).getPostId();
        log.info("게시글이 성공적으로 등록되었습니다. ID: {}", postId);

        return postId;
    }


    private final PostRepository postRepository;
    @Override
    public PostDTO readOne(Long postId) {
        // 게시글을 조회할 때, isVisible이 true인 경우에만 반환
        // findById로 게시글을 찾고, isVisible이 true인 게시글만 필터링
        Post post = postRepository.findById(postId)

                .filter(p -> p.isVisible()) // isVisible이 true인 게시글만 필터링
                .orElseThrow(() -> new EntityNotFoundException("Post not found or post is invisible"));


        return PostDTO.builder()
                .postId(post.getPostId())
                .title(post.getTitle())
                .lat(post.getLat()) // 위도 추가
                .lng(post.getLng()) // 경도 추가
                .contentText(post.getContentText())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpDatedAt())
                .fileNames(post.getImageSet().stream()
                        .map(image -> image.getUuid() + "_" + image.getFileName())
                        .collect(Collectors.toList()))
                .requiredParticipants(post.getRequiredParticipants())
                .status(post.getStatus() != null ? post.getStatus().name() : null)
                .author(post.getUser() != null ? post.getUser().getUsername() : null)
                .build();
    }

    @Override
    public void modify(PostDTO postDTO) {
        Post post = postRepository.findById(postDTO.getPostId()).orElseThrow();

        post.change(postDTO.getTitle(), postDTO.getContentText());
        post.clearAllImages();

        if (postDTO.getFileNames() != null) {
            postDTO.getFileNames().forEach(fileName -> {
                String[] split = fileName.split("_");
                if (split.length == 2) {
                    post.addImage(split[0], split[1]);
                }
            });
        }
        postRepository.save(post);
    }

    private void removeFile(List<String> fileNames) {
        for (String fileName : fileNames) {
            try {
                Path filePath = Paths.get(uploadPath, fileName);
                Files.deleteIfExists(filePath); // 원본 파일 삭제
                log.info("Deleted file: " + filePath);

                // 썸네일 이미지 삭제 (s_ prefix가 있는 파일로 가정)
                Path thumbnailPath = Paths.get(uploadPath, "s_" + fileName);
                Files.deleteIfExists(thumbnailPath); // 썸네일 파일 삭제
                log.info("Deleted thumbnail: " + thumbnailPath);

            } catch (IOException e) {
                log.error("Error deleting file: " + fileName, e);
            }
        }
    }


    //삭제
    @Override
    public void remove(Long postId) {
        Optional<Post> postOptional = postRepository.findById(postId);

        if (postOptional.isPresent()) {
            Post post = postOptional.get();

            // 이미지 파일 삭제
            List<String> fileNames = post.getOriginalImageLinks();
            if (fileNames != null && !fileNames.isEmpty()) {
                removeFile(fileNames);
            }

            // 이미지 연관 관계 제거
            post.clearAllImages();

            log.info("=============="+postId);
            // 게시글 삭제
            postRepository.deleteById(postId);
            log.info("Deleted post with ID: " + postId);
        } else {
            log.warn("Post with ID " + postId + " does not exist.");
        }
    }




    //페이징
    @Override
    public PageResponseDTO<PostDTO> list(PageRequestDTO pageRequestDTO) {
        String[] types = pageRequestDTO.getTypes();
        String keyword = pageRequestDTO.getKeyword();
        Pageable pageable = pageRequestDTO.getPageable("postId");
        Page<Post> result =postRepository.searchAll(types,keyword, pageable);
        log.info("-------------------"+keyword);

//                 isVisible이 true인 게시글만 조회
//        Page<Post> result = postRepository.findByIsVisibleTrue(pageable);  // isVisible이 true인 게시글만 조회

        // 조회된 게시글 수 로그로 출력
        log.info("조회된 게시글 수: {}", result.getTotalElements());

        // 게시글 정보를 PostDTO로 변환하여 리스트에 담음
        List<PostDTO> dtoList = result.getContent().stream()
                .map(post -> PostDTO.builder()
                        .postId(post.getPostId()) // 게시글 ID
                        .title(post.getTitle()) // 제목
                        .contentText(post.getContentText()) // 본문 내용
                        .createdAt(post.getCreatedAt()) // 생성일
                        .updatedAt(post.getUpDatedAt()) // 수정일
                        .fileNames(post.getImageSet().stream() // 첨부된 파일들의 링크
                                .map(image -> image.getUuid() + "_" + image.getFileName())
                                .collect(Collectors.toList()))
                        .requiredParticipants(post.getRequiredParticipants()) // 모집 인원
                        .status(post.getStatus() != null ? post.getStatus().name() : null) // 모집 상태
                        .author(post.getUser() != null ? post.getUser().getUsername() : null) // 작성자 정보
                        .build()
                )
                .collect(Collectors.toList()); // PostDTO 리스트로 변환

        // 게시글 DTO 리스트와 함께 페이징 처리된 결과 반환
        log.info("게시글 DTO 리스트: {}", dtoList); // 반환되는 게시글 리스트 확인용 로그

        return PageResponseDTO.<PostDTO>withAll()
                .pageRequestDTO(pageRequestDTO) // 페이지 요청 정보
                .dtoList(dtoList) // 게시글 DTO 리스트
                .total((int) result.getTotalElements()) // 총 게시글 수
                .build(); // PageResponseDTO 반환
    }

    @Override
    public void markPostAsInvisible(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found"));

        // 게시글을 비공개 처리 (isVisible을 false로 설정)
        post.setIsVisible(false);
        postRepository.save(post);  // 변경 사항 저장
    }




}
