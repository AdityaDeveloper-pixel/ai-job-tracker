package com.jobtracker.jobservice.repository;

import com.jobtracker.jobservice.entity.JobApplication;
import com.jobtracker.jobservice.entity.User;
import com.jobtracker.jobservice.enums.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    List<JobApplication> findByUser(User user);

    Optional<JobApplication> findByIdAndUser(Long id, User user);

    long countByUser(User user);

    long countByUserAndStatus(User user, ApplicationStatus status);

    @Query("SELECT AVG(j.fitScore) FROM JobApplication j WHERE j.user = :user AND j.fitScore IS NOT NULL")
    Double findAverageFitScoreByUser(@Param("user") User user);

    @Query("SELECT j FROM JobApplication j WHERE j.user = :user AND j.source = 'COLD_MAIL' " +
            "AND j.followUpDone = false " +
            "AND j.followUpDate <= :today " +
            "AND j.status = 'APPLIED'")
    List<JobApplication> findPendingFollowUpsByUser(@Param("user") User user,
                                                    @Param("today") LocalDate today);
}