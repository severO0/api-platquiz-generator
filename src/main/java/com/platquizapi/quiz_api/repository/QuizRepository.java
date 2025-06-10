package com.platquizapi.quiz_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.platquizapi.quiz_api.entity.QuizEntity;

public interface QuizRepository extends JpaRepository<QuizEntity, Long> {

}
