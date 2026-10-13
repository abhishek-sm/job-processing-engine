package com.abhi.jobengine.repository;

import com.abhi.jobengine.domain.Job;
import com.abhi.jobengine.domain.JobStatus;
import com.abhi.jobengine.domain.JobType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** Criteria-API filters for {@link JobRepository}. Values are always bound as parameters. */
public final class JobSpecifications {

    private JobSpecifications() {
    }

    /** Matches jobs equal to every non-null filter; with no filters it matches all jobs. */
    public static Specification<Job> matching(String tenantId, JobStatus jobStatus, JobType jobType) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(tenantId)) {
                predicates.add(cb.equal(root.get("tenantId"), tenantId));
            }
            if (jobStatus != null) {
                predicates.add(cb.equal(root.get("jobStatus"), jobStatus));
            }
            if (jobType != null) {
                predicates.add(cb.equal(root.get("jobType"), jobType));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
