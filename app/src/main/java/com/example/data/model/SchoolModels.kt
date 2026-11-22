package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SchoolInfo {
    const val SCHOOL_ID = "dps_etah"
    const val NAME = "Doon Public School, Etah"
    const val SHORT_NAME = "Doon Public School, Etah"
    const val AFFILIATION = "CBSE Affiliated (Nursery to Class XII)"
    const val TRUST = "Managed by Murli Krishna Educational Trust"
    const val MOTTO = "Where Excellence is a Habit! — Excelling with Legacy, Leading with Purpose"
    const val ADDRESS = "4th Milestone, Opposite PAC, Kasganj Road, Aruna Nagar, Etah – 207001 (U.P.)"
    const val PHONES = "+91 76176 14471, +91 76176 14470"
    const val EMAIL = "doonpublicschooletah@gmail.com"
}

data class AcademicWing(
    val id: String,
    val name: String,
    val subtitle: String,
    val classesLabel: String,
    val classes: List<String>,
)

object SchoolCatalog {
    val wings = listOf(
        AcademicWing("doon-kidz", "Doon Kidz", "Pre-Primary Wing", "Nursery, LKG, UKG", listOf("Nursery", "LKG", "UKG")),
        AcademicWing("lower-school", "Lower School", "Primary Wing", "Classes I to V", listOf("Class I", "Class II", "Class III", "Class IV", "Class V")),
        AcademicWing("middle-school", "Middle School", "Upper Primary Wing", "Classes VI to VIII", listOf("Class VI", "Class VII", "Class VIII")),
        AcademicWing("upper-school", "Upper School", "Senior Secondary Wing", "Classes IX to XII", listOf("Class IX", "Class X", "Class XI", "Class XII")),
    )

    val allClasses: List<String> = wings.flatMap { it.classes }
    val sections: List<String> = listOf("A", "B", "C")
    val dptWeeks: List<String> = listOf(
        "Week 1 (Nov 2026)",
        "Week 2 (Nov 2026)",
        "Week 3 (Nov 2026)",
        "Week 4 (Nov 2026)",
    )
    val dptNotHeldReasons: List<String> = listOf(
        "Practical Lab Session",
        "Topic in Progress",
        "School Activity / Event",
        "Revision & Doubt Session",
        "Unit / Term Exam Schedule",
    )
    val craftCategories: List<String> = listOf(
        "Art & Craft",
        "Science Model",
        "Eco Recycling",
        "Robotics & STEM",
        "Clay & Sculpture",
        "Skill Showcase",
    )

    fun wingForClass(className: String): String =
        wings.firstOrNull { className in it.classes }?.name ?: "Upper School"

    fun subjectsForClass(className: String): List<String> = when (className) {
        "Nursery", "LKG", "UKG" -> listOf(
            "English",
            "Hindi",
            "Mathematics",
            "Rhymes & Phonics",
            "General Awareness",
            "EVS & Nature",
            "Art & Craft",
            "Storytelling",
            "Music & Movement",
            "Physical Activity",
        )
        "Class I", "Class II", "Class III", "Class IV", "Class V" -> listOf(
            "English",
            "Hindi",
            "Mathematics",
            "EVS",
            "Computer Science",
            "General Knowledge",
            "Moral Science",
            "Art & Craft",
            "Music & Dance",
            "Physical Education",
            "Reasoning & Aptitude",
        )
        "Class VI", "Class VII", "Class VIII" -> listOf(
            "English",
            "Hindi",
            "Sanskrit",
            "Mathematics",
            "Science",
            "Physics",
            "Chemistry",
            "Biology",
            "Social Science",
            "History & Civics",
            "Geography",
            "Computer Science & AI",
            "General Knowledge",
            "Moral Science",
            "Art & Craft",
            "Physical Education",
        )
        "Class IX", "Class X" -> listOf(
            "English",
            "Hindi",
            "Sanskrit",
            "Mathematics",
            "Science",
            "Physics",
            "Chemistry",
            "Biology",
            "Social Science",
            "History",
            "Geography",
            "Political Science",
            "Economics",
            "Information Technology (IT)",
            "Artificial Intelligence (AI)",
            "Computer Applications",
            "Physical Education",
            "Art & Culture",
        )
        else -> listOf(
            "English Core",
            "Hindi Core",
            "Mathematics",
            "Applied Mathematics",
            "Physics",
            "Chemistry",
            "Biology",
            "Computer Science",
            "Informatics Practices",
            "Accountancy",
            "Business Studies",
            "Economics",
            "History",
            "Geography",
            "Political Science",
            "Sociology",
            "Psychology",
            "Physical Education",
            "Fine Arts",
        )
    }

    val allSubjects: List<String> = allClasses.flatMap { subjectsForClass(it) }.distinct()

    /**
     * Generates the next sequential student roll number: 001, 002, 003...
     * Increments from the highest existing pure numeric roll number, skipping collisions.
     */
    fun nextStudentRollNumber(users: List<SchoolUser>): String {
        val existingIds = users.map { it.userId.trim().uppercase(Locale.US) }.toSet()
        val maxNumeric = users
            .filter { it.role == "STUDENT" }
            .mapNotNull { u -> u.userId.trim().takeIf { it.matches(Regex("^\\d+$")) }?.toIntOrNull() }
            .maxOrNull() ?: 0
        var next = maxNumeric + 1
        while (next.toString().padStart(3, '0') in existingIds) {
            next += 1
        }
        return next.toString().padStart(3, '0')
    }

    data class TimetableSlot(
        val period: String,
        val time: String,
        val mon: String,
        val tue: String,
        val wed: String,
        val thu: String,
        val fri: String,
        val sat: String,
    )

    fun TimetableSlot.toSlotMap(): Map<String, String> = mapOf(
        "period" to period,
        "time" to time,
        "mon" to mon,
        "tue" to tue,
        "wed" to wed,
        "thu" to thu,
        "fri" to fri,
        "sat" to sat,
    )

    fun slotFromMap(map: Map<String, String>): TimetableSlot = TimetableSlot(
        period = map["period"].orEmpty(),
        time = map["time"].orEmpty(),
        mon = map["mon"].orEmpty(),
        tue = map["tue"].orEmpty(),
        wed = map["wed"].orEmpty(),
        thu = map["thu"].orEmpty(),
        fri = map["fri"].orEmpty(),
        sat = map["sat"].orEmpty(),
    )

    fun defaultTimetableSlotsFor(className: String, section: String): List<TimetableSlot> {
        val wing = wingForClass(className)
        return when (wing) {
            "Doon Kidz" -> listOf(
                TimetableSlot("Assembly", "08:30 - 08:50", "Morning Rhymes", "Action Songs", "Yoga & Stretching", "Story Time", "Prayer & Manners", "Fun Assembly"),
                TimetableSlot("Period 1", "08:50 - 09:30", "English Phonics", "English Writing", "English Phonics", "English Reading", "English Rhymes", "Picture Talk"),
                TimetableSlot("Period 2", "09:30 - 10:10", "Number Magic", "Counting & Shapes", "Number Magic", "Number Tracing", "Mental Games", "Activity Corner"),
                TimetableSlot("Recess", "10:10 - 10:35", "Fruit & Lunch Break", "Fruit & Lunch Break", "Fruit & Lunch Break", "Fruit & Lunch Break", "Fruit & Lunch Break", "Fruit & Lunch Break"),
                TimetableSlot("Period 3", "10:35 - 11:15", "Hindi Swar/Vyanjan", "Hindi Rhymes", "Hindi Writing", "Hindi Reading", "General Awareness", "Colouring"),
                TimetableSlot("Period 4", "11:15 - 11:55", "Clay & Craft", "Outdoor Play", "Music & Dance", "Puzzle Play", "Art & Drawing", "Show & Tell"),
            )
            "Lower School" -> listOf(
                TimetableSlot("Assembly", "08:00 - 08:25", "Prayer & News", "Thought & Quiz", "PT / Yoga", "Value Talk", "House Activity", "Club Assembly"),
                TimetableSlot("Period 1", "08:25 - 09:05", "English", "Mathematics", "English", "Mathematics", "English", "Reading Club"),
                TimetableSlot("Period 2", "09:05 - 09:45", "Mathematics", "English", "Mathematics", "EVS", "Mathematics", "Mental Maths"),
                TimetableSlot("Period 3", "09:45 - 10:25", "EVS", "Hindi", "EVS", "English", "Hindi", "Science Activity"),
                TimetableSlot("DPT Slot", "10:25 - 10:45", "Daily Practice Test", "Daily Practice Test", "Daily Practice Test", "Daily Practice Test", "Daily Practice Test", "Weekly Recap"),
                TimetableSlot("Recess", "10:45 - 11:10", "Lunch Break", "Lunch Break", "Lunch Break", "Lunch Break", "Lunch Break", "Lunch Break"),
                TimetableSlot("Period 4", "11:10 - 11:50", "Hindi", "Computer", "Hindi", "Hindi", "EVS", "Art & Craft"),
                TimetableSlot("Period 5", "11:50 - 12:30", "Computer", "Library", "Sports / Games", "Computer", "Games", "Co-Curricular"),
            )
            else -> {
                val shift = when (section.uppercase(Locale.US)) {
                    "B" -> 1
                    "C" -> 2
                    else -> 0
                }
                val core = listOf("Mathematics", "Science", "English", "Social Science", "Hindi / Sanskrit", "Computer / IT")
                fun subAt(idx: Int) = core[(idx + shift) % core.size]
                listOf(
                    TimetableSlot("Assembly", "08:00 - 08:25", "Prayer & News", "Thought & Quiz", "PT / Yoga", "Value Talk", "House Activity", "Club Assembly"),
                    TimetableSlot("Period 1", "08:25 - 09:05", subAt(0), subAt(1), subAt(0), subAt(2), subAt(0), "DPT Evaluation"),
                    TimetableSlot("Period 2", "09:05 - 09:45", subAt(2), subAt(0), subAt(1), subAt(0), subAt(2), "English Grammar"),
                    TimetableSlot("Period 3", "09:45 - 10:25", subAt(1), subAt(2), "${subAt(1)} (Lab)", subAt(3), subAt(1), "Science Quiz"),
                    TimetableSlot("DPT Slot", "10:25 - 10:45", "Daily Practice Test", "Daily Practice Test", "Daily Practice Test", "Daily Practice Test", "Daily Practice Test", "Weekly Recap"),
                    TimetableSlot("Recess", "10:45 - 11:10", "Lunch Break", "Lunch Break", "Lunch Break", "Lunch Break", "Lunch Break", "Lunch Break"),
                    TimetableSlot("Period 4", "11:10 - 11:50", subAt(3), subAt(4), subAt(3), subAt(1), subAt(3), "Library"),
                    TimetableSlot("Period 5", "11:50 - 12:30", subAt(4), subAt(3), subAt(4), subAt(4), subAt(5), "Art & Craft"),
                    TimetableSlot("Period 6", "12:30 - 01:10", subAt(5), "Sports / Games", subAt(5), "Library", "Sports / Games", "Co-Curricular"),
                )
            }
        }
    }

    val standardTimetable = defaultTimetableSlotsFor("Class X", "A")

    fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}

@IgnoreExtraProperties
data class SchoolUser(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val role: String = "STUDENT", // ADMIN | TEACHER | FEE_MANAGER | STUDENT
    val primaryRole: String = "",
    val roles: List<String> = emptyList(),
    val extraRoles: List<String> = emptyList(),
    val userId: String = "",
    val password: String = "",
    val fullName: String = "",
    val phone: String = "",
    val wing: String = "",
    val className: String = "",
    val section: String = "",
    val subject: String = "",
    val parentName: String = "",
    val monthlyTuition: Long = 0L,
    val transportFee: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

fun SchoolUser.effectiveRoles(): List<String> {
    val base = primaryRole.ifBlank { role }
    return (listOf(base) + roles + extraRoles)
        .map { it.trim().uppercase(Locale.US) }
        .filter { it in setOf("ADMIN", "TEACHER", "FEE_MANAGER", "STUDENT") }
        .distinct()
        .ifEmpty { listOf(role) }
}

@IgnoreExtraProperties
data class GatewayConfig(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val merchantUpi: String = "doonpublicetah@sbi",
    val payeeName: String = "Doon Public School Etah",
    val bankName: String = "State Bank of India (Aruna Nagar, Etah)",
    val accountNo: String = "39481020004512",
    val ifsc: String = "SBIN0004512",
    val razorpayKeyId: String = "",
    val latestVersionCode: Long = 3L,
    val latestVersionName: String = "1.2",
    val apkDownloadUrl: String = "",
    val githubRepoSlug: String = "",
    val updateNotes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Circular(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val title: String = "",
    val category: String = "Notice",
    val audience: String = "All Wings",
    val targetWing: String = "",
    val targetClass: String = "",
    val targetSection: String = "",
    val date: String = "",
    val content: String = "",
    val postedBy: String = "",
    val recipientDocId: String = "", // Empty = broadcast; non-empty = targeted to specific user doc ID
    val recipientName: String = "",
    val senderRole: String = "ADMIN", // ADMIN | TEACHER
    val attachmentName: String = "",
    val attachmentMimeType: String = "",
    val attachmentData: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class DptDaily(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val date: String = "",
    val className: String = "",
    val section: String = "",
    val subject: String = "",
    val assessmentType: String = "DPT", // DPT | EXAM
    val held: Boolean = true,
    val topic: String = "",
    val maxMarks: Long = 0L, // 0..100
    val notes: String = "",
    val reason: String = "",
    val attachmentName: String = "",
    val attachmentMimeType: String = "",
    val attachmentData: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class DptWeekly(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val week: String = "",
    val className: String = "",
    val section: String = "",
    val subject: String = "",
    val studentDocId: String = "",
    val studentName: String = "",
    val admissionNo: String = "",
    val scored: Long = 0L,
    val maxMarks: Long = 20L,
    val teacherId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AttendanceRecord(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val date: String = "",
    val className: String = "",
    val section: String = "",
    val studentDocId: String = "",
    val status: String = "Present", // Present | Absent | Leave
    val markedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class LeaveRequest(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val studentDocId: String = "",
    val studentName: String = "",
    val admissionNo: String = "",
    val className: String = "",
    val section: String = "",
    val fromDate: String = "",
    val toDate: String = "",
    val reason: String = "",
    val status: String = "Pending", // Pending | Approved | Rejected
    val teacherRemark: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class HomeworkItem(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val className: String = "",
    val section: String = "",
    val subject: String = "",
    val title: String = "",
    val description: String = "",
    val dueDate: String = "",
    val teacherName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class TermMarkCard(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val term: String = "",
    val className: String = "",
    val section: String = "",
    val studentDocId: String = "",
    val studentName: String = "",
    val admissionNo: String = "",
    val marks: Map<String, Map<String, Long>> = emptyMap(),
    val remarks: String = "",
    val publishedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FeeItem(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val studentDocId: String = "",
    val title: String = "",
    val month: String = "",
    val amount: Long = 0L,
    val status: String = "UNPAID", // UNPAID | PENDING_VERIFICATION | PAID
    val paymentId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FeePayment(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val studentDocId: String = "",
    val studentName: String = "",
    val admissionNo: String = "",
    val className: String = "",
    val feeItemIds: List<String> = emptyList(),
    val feeSummary: String = "",
    val amount: Long = 0L,
    val mode: String = "UPI", // UPI | RAZORPAY
    val utrOrRef: String = "",
    val upiApp: String = "",
    val status: String = "PENDING", // PENDING | VERIFIED | REJECTED
    val receiptNo: String = "",
    val rejectReason: String = "",
    val verifiedBy: String = "",
    val verifiedAtStr: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class DoubtMessage(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val studentDocId: String = "",
    val studentName: String = "",
    val className: String = "",
    val subject: String = "",
    val message: String = "",
    val reply: String = "",
    val repliedBy: String = "",
    val status: String = "OPEN", // OPEN | REPLIED
    val dateLabel: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class CraftProject(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val title: String = "",
    val category: String = "Art & Craft",
    val description: String = "",
    val materials: String = "",
    val authorDocId: String = "",
    val authorName: String = "",
    val authorUserId: String = "",
    val authorRole: String = "STUDENT", // STUDENT | TEACHER | ADMIN
    val classInfo: String = "",
    val dateLabel: String = "",
    val aiImageData: String = "",
    val aiPromptUsed: String = "",
    val likesCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class CampusPoster(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val title: String = "",
    val subtitle: String = "",
    val date: String = "",
    val imageData: String = "",
    val postedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ClassTimetable(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val className: String = "",
    val section: String = "",
    val slots: List<Map<String, String>> = emptyList(),
    val updatedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class CraftLike(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val projectId: String = "",
    val likerId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

enum class FeeBillScope(val apiValue: String, val label: String) {
    CLASSES("CLASSES", "Classes"),
    STUDENTS("STUDENTS", "Students"),
    ENTIRE_SCHOOL("ALL", "Entire School"),
}

enum class FeeBillType(val apiValue: String, val label: String) {
    BOTH("BOTH", "Account Bill (Tuition + Transport)"),
    TUITION("TUITION", "Tuition Fee Only"),
    TRANSPORT("TRANSPORT", "Transport / Bus Only"),
    MISC("MISC", "Extra / Misc Fee"),
}

data class FeeBillRequest(
    val scope: FeeBillScope,
    val selectedClasses: Set<String> = emptySet(),
    val sectionFilter: String = "",
    val selectedStudentDocIds: Set<String> = emptySet(),
    val billType: FeeBillType,
    val month: String,
    val miscTitle: String = "",
    val miscAmount: Long = 0L,
)

data class FeeBillResult(
    val itemsCreated: Int,
    val studentsBilled: Int,
    val skippedDuplicates: Int,
    val totalStudentsMatched: Int,
)
