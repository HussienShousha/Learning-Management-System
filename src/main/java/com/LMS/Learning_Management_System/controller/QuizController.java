package com.LMS.Learning_Management_System.controller;

import com.LMS.Learning_Management_System.dto.GradingDto;
import com.LMS.Learning_Management_System.dto.QuestionDto;
import com.LMS.Learning_Management_System.dto.QuizDto;
import com.LMS.Learning_Management_System.service.QuizService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/quiz")
public class QuizController {
    private final QuizService quizService;

    public QuizController( QuizService quizService) {
        this.quizService = quizService;
    }

    @GetMapping("/quiz_id/{id}")
    public ResponseEntity<String> getQuizById(@PathVariable int id, HttpServletRequest request) {
        try {
            QuizDto quizDTO = quizService.getQuizByID(id , request);
            return ResponseEntity.ok(quizDTO.toString());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @GetMapping("/active_quiz/{courseId}")
    public ResponseEntity<String> getActiveQuiz(@PathVariable int courseId, HttpServletRequest request) {
        try {
            String quizId = quizService.getActiveQuiz(courseId , request);
            return ResponseEntity.ok(quizId);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/add_quiz")
    public ResponseEntity<String> addQuiz(@RequestBody QuizDto quizDto, HttpServletRequest request)
    {
        try {
            int quizId = quizService.create(quizDto.getCourse_id(),quizDto.getType(), request);
            return ResponseEntity.ok("Quiz created successfully. Use this id: " + quizId + " to enter the quiz");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @PostMapping("/add_questions_bank")
    public ResponseEntity<String> addQuestionsBank(@RequestBody QuizDto quizDto, HttpServletRequest request)
    {
        try {
            quizService.createQuestionBank(quizDto.getCourse_id(),quizDto.getQuestionList(),request);
            return ResponseEntity.ok("Question bank created successfully for the course id: "+quizDto.getCourse_id());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @PostMapping("/add_questions")
    public ResponseEntity<String> addQuestions(@RequestBody QuestionDto questionDto, HttpServletRequest request)
    {
        try {
            quizService.addQuestion(questionDto,request);
            return ResponseEntity.ok("Question added successfully for the course id: "+questionDto.getCourse_id());
        } catch (Exception  e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/get_question_bank/{id}")
    public ResponseEntity<String> getQuestionBank(@PathVariable int id, HttpServletRequest request)
    {
        try {
            QuizDto quizDto=quizService.getQuestionBank(id,request);
            return ResponseEntity.ok(quizDto.getQuestionList().toString());
        } catch (Exception  e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/grade_quiz")
    public ResponseEntity<String> gradeQuiz(@RequestBody GradingDto gradingDto, HttpServletRequest request)
    {
        try {
            quizService.gradeQuiz(gradingDto,request);
            return ResponseEntity.ok("Quiz has been graded for the student");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // get student quiz grades
    @GetMapping("/get_quiz_grade/{quizId}/student/{studentId}")
    public ResponseEntity<Integer> getQuizGradeByStudent(@PathVariable int quizId, @PathVariable int studentId, HttpServletRequest request)
    {
        try {
            int grade = quizService.quizFeedback(quizId, studentId,request);
            return ResponseEntity.ok(grade);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Integer.valueOf(e.getMessage()));
        }
    }

    // get quiz questions
    @GetMapping("/get_quiz_questions/{id}")
    public ResponseEntity<String> getQuizQuestions(@PathVariable int id, HttpServletRequest request)
    {
        try {
            return ResponseEntity.ok(quizService.getQuizQuestions(id,request).toString());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/grades/{quizId}")
    public ResponseEntity <List<String>> trackQuizGrades (@PathVariable int quizId, HttpServletRequest request)
    {
        try
        {
            List <String> submissions = quizService.quizGrades(quizId, request);
            return ResponseEntity.ok(submissions);
        }
        catch (IllegalArgumentException e)
        {
            return ResponseEntity.badRequest().body(Collections.singletonList(e.getMessage()));
        }
    }
}
