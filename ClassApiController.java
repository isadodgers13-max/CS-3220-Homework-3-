// src/main/java/.../dto/StudentDto.java
public class StudentDto {
    private Long id;
    private String name;
    private Integer age;
    // constructors, getters, setters
}

// src/main/java/.../dto/ClassSummaryDto.java
public class ClassSummaryDto {
    private Long id;
    private String session;
    private String time;
    private String level;
    private int studentCount;
    // constructors, getters, setters
}

// src/main/java/.../dto/ClassDetailDto.java
public class ClassDetailDto {
    private Long id;
    private String session;
    private String time;
    private String level;
    private List<StudentDto> students;
    // constructors, getters, setters
}

// src/main/java/.../dto/CreateClassRequest.java
public class CreateClassRequest {
    private String session;
    private String time;
    private String level;
    // getters/setters
}

// src/main/java/.../dto/AddStudentRequest.java
public class AddStudentRequest {
    private Long studentId;
    // getters/setters
}
----------------------------------------------------------------------------------
public interface ClassRepository extends JpaRepository<SwimmingClass, Long> {
    List<SwimmingClass> findBySession(String session);
}

public interface StudentRepository extends JpaRepository<Student, Long> {
    // maybe findByName etc. if you need
}
-----------------------------------------------------------------------------------
// src/main/java/.../controller/ClassApiController.java
@RestController
@RequestMapping("/api/classes")
public class ClassApiController {

    private final ClassRepository classRepo;
    private final StudentRepository studentRepo;

    @Autowired
    public ClassApiController(ClassRepository classRepo, StudentRepository studentRepo) {
        this.classRepo = classRepo;
        this.studentRepo = studentRepo;
    }

    // (a) Get classes in a session
    @GetMapping
    public ResponseEntity<List<ClassSummaryDto>> getClassesBySession(@RequestParam("session") String session) {
        List<SwimmingClass> classes = classRepo.findBySession(session);
        List<ClassSummaryDto> result = classes.stream()
            .map(c -> new ClassSummaryDto(
                c.getId(),
                c.getSession(),
                c.getTime(),
                c.getLevel(),
                c.getStudents() == null ? 0 : c.getStudents().size()
            ))
            .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // (b) Get a class with a given id
    @GetMapping("/{id}")
    public ResponseEntity<ClassDetailDto> getClassById(@PathVariable("id") Long id) {
        Optional<SwimmingClass> opt = classRepo.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        SwimmingClass c = opt.get();
        List<StudentDto> students = (c.getStudents() == null ? Collections.emptySet() : c.getStudents()).stream()
            .map(s -> new StudentDto(s.getId(), s.getName(), s.getAge()))
            .collect(Collectors.toList());
        ClassDetailDto dto = new ClassDetailDto(c.getId(), c.getSession(), c.getTime(), c.getLevel(), students);
        return ResponseEntity.ok(dto);
    }

    // (c) Create a new class
    @PostMapping
    public ResponseEntity<?> createClass(@RequestBody CreateClassRequest req, UriComponentsBuilder uriBuilder) {
        if (req.getSession() == null || req.getTime() == null || req.getLevel() == null) {
            return ResponseEntity.badRequest().body("Missing required field(s): session/time/level");
        }
        SwimmingClass c = new SwimmingClass();
        c.setSession(req.getSession());
        c.setTime(req.getTime());
        c.setLevel(req.getLevel());
        c.setStudents(new HashSet<>());
        SwimmingClass saved = classRepo.save(c);
        URI location = uriBuilder.path("/api/classes/{id}").buildAndExpand(saved.getId()).toUri();
        return ResponseEntity.created(location).body(new ClassSummaryDto(saved.getId(), saved.getSession(), saved.getTime(), saved.getLevel(), 0));
    }

    // (d) Add a student to a class
    @PostMapping("/{id}/students")
    public ResponseEntity<?> addStudentToClass(@PathVariable("id") Long classId, @RequestBody AddStudentRequest req) {
        if (req.getStudentId() == null) {
            return ResponseEntity.badRequest().body("studentId required");
        }

        Optional<SwimmingClass> optClass = classRepo.findById(classId);
        if (optClass.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Class not found");
        }
        SwimmingClass swimClass = optClass.get();

        Optional<Student> optStudent = studentRepo.findById(req.getStudentId());
        if (optStudent.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Student not found");
        }
        Student student = optStudent.get();

        // Check whether student is already in another class (implement logic according to your model)
        // Example: Student has getAssignedClass() or check repositories for membership
        if (student.getSwimmingClass() != null && !student.getSwimmingClass().getId().equals(classId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Student already assigned to another class");
        }

        // Add student to the class set (handle bidirectional relationship if present)
        swimClass.getStudents().add(student);
        student.setSwimmingClass(swimClass); // if Student has reference

        classRepo.save(swimClass);
        studentRepo.save(student);

        return ResponseEntity.ok().body("Student added");
    }
}
