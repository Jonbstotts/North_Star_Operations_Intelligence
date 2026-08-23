package com.wtm.employee;

import com.wtm.config.AppConfig;
import com.wtm.model.CelebrationConfig;
import com.wtm.security.AuditService;
import com.wtm.security.AuthorizationService;
import com.wtm.security.Permission;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Management-facing Employee Operations facade.
 *
 * The employee repository is authoritative. Legacy CelebrationConfig records
 * are maintained only as a compatibility projection for existing Main Showcase
 * code and are rebuilt from Employee Operations when employee data changes.
 */
public final class EmployeeService {
    private EmployeeService(){}

    public static EmployeeStore.Snapshot load(){
        AuthorizationService.require(Permission.EMPLOYEE_OPERATIONS);
        return EmployeeStore.load();
    }

    public static EmployeeStore.Snapshot loadForSystem(){
        return EmployeeStore.load();
    }

    public static void save(
            EmployeeStore.Snapshot snapshot,
            AppConfig config
    ){
        AuthorizationService.require(Permission.EMPLOYEE_OPERATIONS);
        validate(snapshot);
        EmployeeStore.save(snapshot);
        if(config!=null)syncCelebrations(config,snapshot);
        AuditService.record("Updated Employee Operations records");
    }

    public static boolean migrateLegacyCelebrationsIfNeeded(AppConfig config){
        if(config==null||config.celebrations.isEmpty())return false;

        EmployeeStore.Snapshot snapshot=EmployeeStore.load();
        if(!snapshot.employees.isEmpty())return false;

        for(CelebrationConfig c:config.celebrations){
            MonthDay birthday=null;
            if(c.birthdayMonth()>0&&c.birthdayDay()>0){
                try{birthday=MonthDay.of(c.birthdayMonth(),c.birthdayDay());}
                catch(Exception ignored){}
            }

            snapshot.employees.add(new EmployeeProfile(
                    UUID.randomUUID().toString(),
                    "",
                    c.name(),
                    "",
                    "",
                    "",
                    c.hireDate(),
                    birthday,
                    "",
                    c.photoAsset(),
                    c.enabled(),
                    true,
                    c.showBirthday(),
                    c.showAnniversary(),
                    c.employeeOfMonth(YearMonth.now()),
                    "",
                    "",
                    0
            ));
        }

        EmployeeStore.save(snapshot);
        syncCelebrations(config,snapshot);
        AuditService.record("Migrated legacy celebration records into Employee Operations");
        return true;
    }

    public static void syncCelebrations(
            AppConfig config,
            EmployeeStore.Snapshot snapshot
    ){
        if(config==null||snapshot==null)return;

        config.celebrations.clear();
        YearMonth month=YearMonth.now();

        for(EmployeeProfile e:snapshot.employees){
            MonthDay birthday=e.birthday();
            config.celebrations.add(new CelebrationConfig(
                    e.name(),
                    birthday==null?0:birthday.getMonthValue(),
                    birthday==null?0:birthday.getDayOfMonth(),
                    e.hireDate(),
                    e.photoAsset(),
                    e.showBirthday(),
                    e.showAnniversary(),
                    e.employeeOfMonth()?month.getYear():0,
                    e.employeeOfMonth()?month.getMonthValue():0,
                    true,
                    e.active()&&e.celebrationAnnouncements()
            ));
        }
    }

    public static List<EmployeeProfile> activeEmployees(
            EmployeeStore.Snapshot snapshot
    ){
        if(snapshot==null)return List.of();
        return snapshot.employees.stream()
                .filter(EmployeeProfile::active)
                .sorted(Comparator.comparing(EmployeeProfile::name))
                .toList();
    }

    public static EmployeeProfile findByEmployeeNumber(
            EmployeeStore.Snapshot snapshot,
            String number
    ){
        if(snapshot==null||number==null)return null;
        String key=number.trim();

        return snapshot.employees.stream()
                .filter(EmployeeProfile::active)
                .filter(e->key.equalsIgnoreCase(e.employeeNumber()))
                .findFirst()
                .orElse(null);
    }

    public static EmployeeProfile findById(
            EmployeeStore.Snapshot snapshot,
            String id
    ){
        if(snapshot==null||id==null)return null;
        return snapshot.employees.stream()
                .filter(e->id.equals(e.id()))
                .findFirst()
                .orElse(null);
    }

    public static Set<String> activeQualifications(
            EmployeeStore.Snapshot snapshot,
            String employeeId,
            LocalDate today
    ){
        if(snapshot==null||employeeId==null)return Set.of();
        LocalDate day=today==null?LocalDate.now():today;

        return snapshot.training.stream()
                .filter(r->employeeId.equals(r.employeeId()))
                .filter(r->r.currentlyQualified(day))
                .map(TrainingRecord::qualification)
                .filter(q->q!=null&&!q.isBlank())
                .collect(Collectors.toCollection(
                        ()->new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
    }

    public static boolean unavailable(
            EmployeeStore.Snapshot snapshot,
            String employeeId,
            LocalDate date
    ){
        if(snapshot==null||employeeId==null)return false;
        LocalDate day=date==null?LocalDate.now():date;

        return snapshot.attendance.stream()
                .filter(r->employeeId.equals(r.employeeId()))
                .filter(r->day.equals(r.date()))
                .anyMatch(AttendanceRecord::unavailableForWholeDay);
    }

    public static List<AssignmentRecommendation> recommendAssignments(
            EmployeeStore.Snapshot snapshot,
            LocalDate date
    ){
        if(snapshot==null)return List.of();
        LocalDate day=date==null?LocalDate.now():date;

        List<EmployeeProfile> available=activeEmployees(snapshot).stream()
                .filter(e->!unavailable(snapshot,e.id(),day))
                .toList();

        Map<String,Integer> assignmentCounts=new HashMap<>();
        List<AssignmentRecommendation> results=new ArrayList<>();

        for(DutyRequirement duty:snapshot.duties){
            for(int slot=1;slot<=duty.requiredCount();slot++){
                EmployeeProfile selected=available.stream()
                        .filter(e->activeQualifications(
                                snapshot,e.id(),day
                        ).stream().anyMatch(q->
                                q.equalsIgnoreCase(duty.qualification())))
                        .min(Comparator
                                .comparingInt((EmployeeProfile e)->
                                        assignmentCounts.getOrDefault(e.id(),0))
                                .thenComparing(EmployeeProfile::name))
                        .orElse(null);

                if(selected==null){
                    results.add(new AssignmentRecommendation(
                            duty.duty(),
                            duty.qualification(),
                            "",
                            "",
                            false,
                            "No available qualified employee"
                    ));
                    continue;
                }

                assignmentCounts.merge(selected.id(),1,Integer::sum);
                results.add(new AssignmentRecommendation(
                        duty.duty(),
                        duty.qualification(),
                        selected.id(),
                        selected.name(),
                        true,
                        "Qualified and available"
                ));
            }
        }

        return results;
    }

    public static AttendanceRecord recordCallIn(
            EmployeeStore.Snapshot snapshot,
            EmployeeProfile employee,
            String type,
            String source,
            String notes,
            String callerPhone,
            String externalReference
    ){
        Objects.requireNonNull(snapshot);
        Objects.requireNonNull(employee);

        AttendanceRecord record=new AttendanceRecord(
                UUID.randomUUID().toString(),
                employee.id(),
                LocalDate.now(),
                LocalTime.now().withSecond(0).withNano(0),
                normalizeCallInType(type),
                source,
                "RECORDED",
                notes,
                callerPhone,
                externalReference
        );
        snapshot.attendance.add(record);
        EmployeeStore.save(snapshot);
        AuditService.record(
                "Recorded "+record.type()+" attendance event for "
                        +employee.name()+" via "+record.source()
        );
        return record;
    }

    public static String normalizeCallInType(String type){
        String value=type==null?"":type.trim().toUpperCase(Locale.ROOT);
        return switch(value){
            case "1","CALL OUT","CALL_OUT","ABSENT"->"CALL_OUT";
            case "2","LATE","RUNNING LATE","RUNNING_LATE"->"RUNNING_LATE";
            case "3","EARLY","LEAVING EARLY","LEAVING_EARLY"->"LEAVING_EARLY";
            default->"OTHER";
        };
    }

    private static void validate(EmployeeStore.Snapshot snapshot){
        Objects.requireNonNull(snapshot);

        Set<String> ids=new HashSet<>();
        Set<String> employeeNumbers=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for(EmployeeProfile e:snapshot.employees){
            if(e.name().isBlank())
                throw new IllegalArgumentException("Employee name is required.");
            if(!ids.add(e.id()))
                throw new IllegalArgumentException("Duplicate employee ID.");

            if(!e.employeeNumber().isBlank()
                    &&!employeeNumbers.add(e.employeeNumber()))
                throw new IllegalArgumentException(
                        "Duplicate employee number: "+e.employeeNumber());
        }
    }

    public record AssignmentRecommendation(
            String duty,
            String qualification,
            String employeeId,
            String employeeName,
            boolean covered,
            String reason
    ){}
}
