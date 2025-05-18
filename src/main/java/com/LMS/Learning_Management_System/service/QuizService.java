package com.LMS.Learning_Management_System.service;

import com.LMS.Learning_Management_System.dto.GradingDto;
import com.LMS.Learning_Management_System.dto.QuestionDto;
import com.LMS.Learning_Management_System.dto.QuizDto;
import com.LMS.Learning_Management_System.repository.*;
import com.LMS.Learning_Management_System.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class QuizService {
    private final QuizRepository quizRepository;
    private final CourseRepository courseRepository;
    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper ;
    private final StudentRepository studentRepository;
    private final GradingRepository gradingRepository;
    private final QuestionTypeRepository questionTypeRepository;
    private final EnrollmentRepository enrollmentRepository;

    private static final Random random = new Random();

    private static final String STUDENT_NOT_FOUND = "No student found with the given ID: ";
    private static final String QUIZ_NOT_FOUND = "No quiz found with the given ID: ";

    List<Question> quizQuestions = new ArrayList<>();
    List<Question>questionBank= new ArrayList<>();
    public QuizService(QuizRepository quizRepository, CourseRepository courseRepository, QuestionRepository questionRepository, ObjectMapper objectMapper, StudentRepository studentRepository, GradingRepository gradingRepository, QuestionTypeRepository questionTypeRepository, EnrollmentRepository enrollmentRepository) {
        this.quizRepository = quizRepository;
        this.courseRepository = courseRepository;
        this.questionRepository = questionRepository;
        this.objectMapper = objectMapper;
        this.studentRepository = studentRepository;
        this.gradingRepository = gradingRepository;
        this.questionTypeRepository = questionTypeRepository;
        this.enrollmentRepository = enrollmentRepository;
    }


    public int create(Integer courseId , int typeId , HttpServletRequest request ) throws IllegalArgumentException {
        Users loggedInInstructor = (Users) request.getSession().getAttribute("user");
        Course course= courseRepository.findById(courseId)
                .orElseThrow(() -> new EntityNotFoundException("Course not found"));
        int instructorId = course.getInstructorId().getUserAccountId();

        if (loggedInInstructor == null)
        {
            throw new IllegalArgumentException("No logged in user is found.");
        }
        else if (loggedInInstructor.getUserTypeId() == null || loggedInInstructor.getUserTypeId().getUserTypeId() != 3)
        {
            throw new IllegalArgumentException("Logged-in user is not an instructor.");
        }
        else if (instructorId != loggedInInstructor.getUserId())
        {
            throw new IllegalArgumentException("Logged-in instructor does not have access for this course.");
        }
        if(typeId>3 || typeId<1) throw new IllegalArgumentException("No such type\n");

        List<Quiz> quizzes =  quizRepository.findAll();
        Quiz quiz = new Quiz();
        quiz.setCourse(course);
        quiz.setTitle("quiz"+(quizzes.size()+1));
        quiz.setQuestionCount(5);
        quiz.setRandomized(true);
        quiz.setCreationDate(new Date());

        generateQuestions(quiz,typeId, course);
        quizRepository.save(quiz);

        return quiz.getQuizId();
    }

    public String getActiveQuiz( int courseId,HttpServletRequest request)
    {
        Users loggedInUser = (Users) request.getSession().getAttribute("user");

        if (loggedInUser == null) {
            throw new IllegalArgumentException("No user is logged in.");
        }

        boolean instructor = courseRepository.findByInstructorId(loggedInUser.getUserId(),courseId);

        if(loggedInUser.getUserTypeId().getUserTypeId()==3)
        {
            if(!instructor)
                throw new IllegalArgumentException("You don't have permission to enter this quiz.");
        }
        else if(loggedInUser.getUserTypeId().getUserTypeId()==2)
        {
            boolean enrolled = enrollmentRepository.existsByStudentAndCourse(studentRepository.findById(loggedInUser.getUserId())
                            .orElseThrow(() -> new IllegalArgumentException(STUDENT_NOT_FOUND))
                    ,courseRepository.findById(courseId)
                            .orElseThrow(() -> new IllegalArgumentException("No Course found with the given ID: " + courseId)));
            if(!enrolled)
                throw new IllegalArgumentException("You are not enrolled this course.");
        }

        List<Quiz> quizIds = quizRepository.getQuizzesByCourseId(courseId);
        StringBuilder ids= new StringBuilder();
        for(Quiz id : quizIds)
        {
            QuizDto quizDto = new QuizDto();
            quizDto.setQuizId(id.getQuizId());
            quizDto.setCreation_date(id.getCreationDate());
           if(id.getCreationDate().getTime()+ 15 * 60 * 1000>new Date().getTime())
               ids.append("quiz with id: ").append(quizDto.getQuizId()).append(" has time left: ")
                       .append(((quizDto.getCreation_date().getTime()+(15* 60 * 1000)-new Date().getTime())/(60*1000))).append("\n");
        }
        if (ids.isEmpty()) return "No Current Quizzes\n overall Quizzes: "+quizIds.size();
        return ids.toString();
    }

    public List<QuestionDto> getQuizQuestions(int id, HttpServletRequest request) throws IllegalArgumentException {
        Users loggedInUser = (Users) request.getSession().getAttribute("user");
        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(QUIZ_NOT_FOUND + id));

        validateUserLoggedIn(request);

        boolean instructor = courseRepository.findByInstructorId(loggedInUser.getUserId(),quiz.getCourse().getCourseId());
        if(loggedInUser.getUserTypeId().getUserTypeId()==3)
        {
            if(!instructor)
                throw new IllegalArgumentException("You don't have permission to enter this quiz.");
        } else if(loggedInUser.getUserTypeId().getUserTypeId()==2)
        {
            boolean enrolled = enrollmentRepository.existsByStudentAndCourse(studentRepository.findById(loggedInUser.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException(STUDENT_NOT_FOUND)),quiz.getCourse());
            quizNoSubmission(quiz, loggedInUser, enrolled);
        }
        quizQuestions = questionRepository.findQuestionsByQuizId(id);
        return getDtos();
    }

    private void validateUserLoggedIn(HttpServletRequest request) {
        Users loggedInUser = (Users) request.getSession().getAttribute("user");

        if (loggedInUser == null) {
            throw new IllegalArgumentException("No user is logged in.");
        }
    }

    private List<QuestionDto> getDtos() {
        List<QuestionDto> questions =new ArrayList<>();
        for (Question q : quizQuestions) {
            QuestionDto questionDto = new QuestionDto();
            questionDto.setOptions(q.getOptions());
            questionDto.setType(q.getQuestionType().getTypeId());
            questionDto.setQuestion_text(q.getQuestionText());
            questionDto.setCorrect_answer(q.getCorrectAnswer());
            questionDto.setCourse_id(q.getCourseId().getCourseId());
            questionDto.setQuestion_id(q.getQuestionId());
            questions.add(questionDto);
        }
        return questions;
    }

    public void addQuestion(QuestionDto questionDto, HttpServletRequest request) throws IllegalArgumentException {
        Users loggedInUser = (Users) request.getSession().getAttribute("user");
        Course course =courseRepository.findById(questionDto.getCourse_id())  // check course
                .orElseThrow(() -> new IllegalArgumentException("No course found with the given ID: " + questionDto.getCourse_id()));

        validateUserLoggedIn(request);

        boolean instructor = courseRepository.findByInstructorId(loggedInUser.getUserId(),course.getCourseId());
        if(loggedInUser.getUserTypeId().getUserTypeId()==3)
        {
            if(!instructor)
                throw new IllegalArgumentException("You don't have permission to enter this course.");
        } else if(loggedInUser.getUserTypeId().getUserTypeId()==2)
        {
                throw new IllegalArgumentException("You don't have permission to use this feature.");
        }
        Optional<Question> optQuestion = questionRepository.findById(questionDto.getQuestion_id());
        if(optQuestion.isPresent()) throw new IllegalArgumentException("question already exists");
        Question question = new Question();
        question.setQuestionText(questionDto.getQuestion_text());
        // Handle QuestionType
        QuestionType questionType = questionTypeRepository.findById(questionDto.getType())
                .orElseThrow(() -> new EntityNotFoundException("No such QuestionType"+questionDto.getType()));
        question.setQuestionType(questionType);
        try {
            // Convert List<String> to JSON string
            String optionsAsString = objectMapper.writeValueAsString(questionDto.getOptions());
            question.setOptions(optionsAsString);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert options to JSON", e);
        }
        question.setCourseId(course);
        question.setCorrectAnswer(questionDto.getCorrect_answer());
        questionRepository.save(question);

    }

    public void generateQuestions(Quiz quiz,int questionType, Course courseId) throws IllegalArgumentException {

        List<Question> allQuestions = questionRepository
                .findQuestionsByCourseIdAndQuestionType(courseId.getCourseId(),questionType);
        List<Question> emptyQuestions = questionRepository
                .findEmptyQuestionsByCourseIdAndQuestionType(courseId.getCourseId(),questionType);
        if(allQuestions.size()< 5 )
            throw new IllegalArgumentException("No enough Questions to create quiz!\n");
        if(emptyQuestions.size() < 5 )
            throw new IllegalArgumentException("No enough unassigned questions to create new quiz! number: "+emptyQuestions.size()+" type "+questionType+"\n");

        Set<Integer> selectedIndices = new HashSet<>();  // To track selected indices
        int count = 0;
        while (count < 5) {
            int randomNumber = random.nextInt(allQuestions.size());

            if (!selectedIndices.contains(randomNumber)) {
                selectedIndices.add(randomNumber);
                Question selectedQuestion = allQuestions.get(randomNumber);
                selectedQuestion.setQuiz(quiz);
                count++;
            }
        }
    }

    public QuizDto getQuizByID (int id, HttpServletRequest request) {
        Users loggedInUser = (Users) request.getSession().getAttribute("user");
        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(QUIZ_NOT_FOUND + id));

        validateUserLoggedIn(request);

        boolean instructor = courseRepository.findByInstructorId(loggedInUser.getUserId(),quiz.getCourse().getCourseId());
        if(loggedInUser.getUserTypeId().getUserTypeId()==3)
        {
            if(!instructor)
                throw new IllegalArgumentException("You don't have permission to enter this quiz.");
        } else if(loggedInUser.getUserTypeId().getUserTypeId()==2)
        {
            boolean enrolled = enrollmentRepository.existsByStudentAndCourse(studentRepository.findById(loggedInUser.getUserId())
                            .orElseThrow(() -> new IllegalArgumentException(STUDENT_NOT_FOUND))
                    ,quiz.getCourse());
            if(!enrolled)
                throw new IllegalArgumentException("You don't have permission to enter this course.");
        }

        return new QuizDto(
                quiz.getQuizId(),
                quiz.getTitle(),
                quiz.getCreationDate()
        );
    }


    public void createQuestionBank(int courseId, List<QuestionDto> questions, HttpServletRequest request) throws IllegalArgumentException {

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new EntityNotFoundException("No such Course"));
        Users loggedInUser = (Users) request.getSession().getAttribute("user");

        if (loggedInUser == null) {
            throw new IllegalArgumentException("No user is logged in.");
        }
        boolean instructor = courseRepository.findByInstructorId(loggedInUser.getUserId(),courseId);
        if(loggedInUser.getUserTypeId().getUserTypeId()==3 && !instructor)
        {
            throw new IllegalArgumentException("You don't have permission to enter this course.");
        }
        if(loggedInUser.getUserTypeId().getUserTypeId()==2)
        {
            throw new IllegalArgumentException("You don't have access to this feature!");
        }

        for (QuestionDto dto : questions) {
            Question question = questionRepository.findById(dto.getQuestion_id())
                    .orElse(new Question()); // Find or create a new question

            question.setQuestionText(dto.getQuestion_text());
            try {
                String optionsAsString = objectMapper.writeValueAsString(dto.getOptions());
                question.setOptions(optionsAsString);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to convert options to JSON", e);
            }
            question.setCorrectAnswer(dto.getCorrect_answer());
            question.setCourseId(course);

            QuestionType questionType = questionTypeRepository.findById(dto.getType())
                    .orElseThrow(() -> new EntityNotFoundException("No such QuestionType"+dto.getType()));
            question.setQuestionType(questionType);

            questionRepository.save(question);
        }
    }

    public QuizDto getQuestionBank(int courseId, HttpServletRequest request) throws IllegalArgumentException {
        Users loggedInUser = (Users) request.getSession().getAttribute("user");

        if (loggedInUser == null) {
            throw new IllegalArgumentException("No user is logged in.");
        }
        boolean instructor = courseRepository.findByInstructorId(loggedInUser.getUserId(),courseId);

        if(loggedInUser.getUserTypeId().getUserTypeId()==3)
        {
            if(!instructor)
                throw new IllegalArgumentException("You don't have permission to enter this course.");
        } else if(loggedInUser.getUserTypeId().getUserTypeId()==2)
        {
                throw new IllegalArgumentException("You don't have permission to enter this feature!");
        }

        QuizDto quizDto = new QuizDto();
        questionBank = questionRepository.findQuestionsByCourseId(courseId);
        if(questionBank.isEmpty()) throw new IllegalArgumentException("this course doesn't have any!");
        List<QuestionDto> questionDtos = getQuestionDtos();
        quizDto.setQuestionList(questionDtos);
        return quizDto;
    }

    private List<QuestionDto> getQuestionDtos() {
        List<QuestionDto> questionDtos = new ArrayList<>();
        for (Question question : questionBank) {
            QuestionDto questionDto = new QuestionDto();
            questionDto.setQuestion_id(question.getQuestionId());
            questionDto.setCorrect_answer(question.getCorrectAnswer());
            questionDto.setQuestion_text(question.getQuestionText());
            questionDto.setType(question.getQuestionType().getTypeId());
            questionDto.setCourse_id(question.getCourseId().getCourseId());
            questionDto.setOptions(question.getOptions());
            questionDtos.add(questionDto);
        }
        return questionDtos;
    }

    // grade quiz
    public void gradeQuiz(GradingDto gradingDto, HttpServletRequest request) throws IllegalArgumentException {
        Optional<Quiz> optionalQuiz= Optional.ofNullable(quizRepository.findById(gradingDto.getQuiz_id())
                .orElseThrow(() -> new EntityNotFoundException("No such Quiz")));
        Quiz quiz = optionalQuiz.orElseThrow(() -> new EntityNotFoundException("No such Quiz"));
        Users loggedInUser = (Users) request.getSession().getAttribute("user");

        if (loggedInUser == null) {
            throw new IllegalArgumentException("No user is logged in.");
        }
        boolean enrolled = enrollmentRepository.existsByStudentAndCourse(studentRepository.findById(loggedInUser.getUserId())
                .orElseThrow(() -> new IllegalArgumentException(STUDENT_NOT_FOUND)),quiz.getCourse());
        if(loggedInUser.getUserTypeId().getUserTypeId()==2)
        {
            quizNoSubmission(quiz, loggedInUser, enrolled);
        }
        else throw new IllegalArgumentException("You are not authorized to submit quizzes! ");
        Student student = studentRepository.findById(loggedInUser.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("No Student found with this ID!"));
          // get questions with the quiz id
        List<Question>gradedQuestions=questionRepository.findQuestionsByQuizId(gradingDto.getQuiz_id());
        List<String> answersList = gradingDto.getAnswers();
        int grade=0;
        for (int i = 0; i < gradedQuestions.size(); i++) {

            if(Objects.equals(gradedQuestions.get(i).getCorrectAnswer(), answersList.get(i)))
            {
                grade++;
            }
        }

        Grading grading = new Grading();
        grading.setGrade(grade);
        grading.setQuiz_id(quiz);
        grading.setStudent_id(student);
        gradingRepository.save(grading);
    }

    private void quizNoSubmission(Quiz quiz, Users loggedInUser, boolean enrolled) {
        if(!enrolled)
            throw new IllegalArgumentException("You don't have permission to enter this course.");
        if(quiz.getCreationDate().getTime()+ 15 * 60 * 1000<new Date().getTime())
            throw new IllegalArgumentException("The quiz has been finished!");
        if (gradingRepository.boolFindGradeByQuizAndStudentID(quiz.getQuizId(),loggedInUser.getUserId()).orElse(false))
            throw new IllegalArgumentException("You have submitted a response earlier!");
    }

    public int quizFeedback(int quizId, int studentId, HttpServletRequest request) throws IllegalArgumentException {
        Users loggedInUser = (Users) request.getSession().getAttribute("user");
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException(QUIZ_NOT_FOUND + quizId));

        if (loggedInUser == null) {
            throw new IllegalArgumentException("No user is logged in.");
        }
        boolean instructor = courseRepository.findByInstructorId(loggedInUser.getUserId(),quiz.getCourse().getCourseId());

        if(loggedInUser.getUserTypeId().getUserTypeId()==3)
        {
            if(!instructor)
                throw new IllegalArgumentException("You don't have permission to enter this quiz.");
        } else if(loggedInUser.getUserTypeId().getUserTypeId()==2)
        {
            boolean enrolled = enrollmentRepository.existsByStudentAndCourse(studentRepository.findById(loggedInUser.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("No student found with this ID!")),quiz.getCourse());
            if(!enrolled)
                throw new IllegalArgumentException("You don't have permission to enter this course.");
            if(loggedInUser.getUserId()!=studentId)
                throw new IllegalArgumentException("You are not authorized to check other student's grades!");
        }
        int grade = gradingRepository.findGradeByQuizAndStudentID(quizId,studentId);
        if(grade ==-1) throw new IllegalArgumentException("Quiz haven't been graded yet");
        return grade;

    }

    public List <String> quizGrades (int quizId, HttpServletRequest request)
    {
        if (quizRepository.existsById(quizId))
        {
            Quiz quiz = quizRepository.findById(quizId).orElseThrow(() -> new IllegalArgumentException(QUIZ_NOT_FOUND + quizId));
            List <Grading> quizGrades = gradingRepository.findAllByQuizId(quiz);
            Users loggedInInstructor = (Users) request.getSession().getAttribute("user");
            int instructorId = quiz.getCourse().getInstructorId().getUserAccountId();

            if (loggedInInstructor == null)
            {
                throw new IllegalArgumentException("No logged in user is found.");
            }
            else if (loggedInInstructor.getUserTypeId() == null || loggedInInstructor.getUserTypeId().getUserTypeId() != 3)
            {
                throw new IllegalArgumentException("Logged-in user is not an instructor.");
            }
            else if (instructorId != loggedInInstructor.getUserId())
            {
                throw new IllegalArgumentException("Logged-in instructor does not have access for this quiz grades.");
            }

            List <String> grades = new ArrayList<>();
            for (Grading grading : quizGrades)
            {
                Student student = grading.getStudent_id();
                String studentGrade = "(ID)" + student.getUserAccountId() + ": (Grade)" + grading.getGrade();
                grades.add(studentGrade);
            }
            return grades;
        }
        else
        {
            throw new IllegalArgumentException("Quiz with ID " + quizId + " not found.");
        }
    }
}
