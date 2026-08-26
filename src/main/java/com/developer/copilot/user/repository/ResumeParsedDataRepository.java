package com.developer.copilot.user.repository;

import com.developer.copilot.user.entity.Resume;
import com.developer.copilot.user.entity.ResumeParsedData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResumeParsedDataRepository extends JpaRepository<ResumeParsedData, Long> {

    Optional<ResumeParsedData> findByResume(Resume resume);

    Optional<ResumeParsedData> findByResumeId(Long resumeId);

    boolean existsByResume(Resume resume);

    void deleteByResume(Resume resume);

    void deleteByResumeIn(List<Resume> resumes);
}
