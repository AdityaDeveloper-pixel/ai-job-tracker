package com.jobtracker.jobservice.repository;

import com.jobtracker.jobservice.entity.JobApplication;
import com.jobtracker.jobservice.enums.ApplicationSource;
import com.jobtracker.jobservice.enums.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    List<JobApplication> findByStatus(ApplicationStatus status);

    List<JobApplication> findBySource(ApplicationSource source);

    List<JobApplication> findByFitScoreGreaterThanEqual(Integer minScore);

    // Find cold mail applications where follow-up is pending
    @Query("SELECT j FROM JobApplication j WHERE j.source = 'COLD_MAIL' " +
           "AND j.followUpDone = false " +
           "AND j.followUpDate <= :today " +
           "AND j.status = 'APPLIED'")
    List<JobApplication> findPendingFollowUps(LocalDate today);

    // Dashboard stats
    long countByStatus(ApplicationStatus status);

    @Query("SELECT AVG(j.fitScore) FROM JobApplication j WHERE j.fitScore IS NOT NULL")
    Double findAverageFitScore();
}
