package com.business.service;

import com.business.entity.Employee;
import com.business.exception.EmailAlreadyExistException;
import com.business.exception.EmployeeAlreadyHasRoleException;
import com.business.exception.UsernameAlreadyExistException;
import com.util.constants.UserGroup;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

@Stateless
public class EmployeeService {

    private final static Logger LOGGER = Logger.getLogger(EmployeeService.class.getName());

    @PersistenceContext(unitName = "itrPU")
    private EntityManager em;

    // Insert employee into database
    public void registerEmployee(String firstname, String lastname, String username, String password,
            String email, String address, String phoneNumber) {

        if (isUniqueEmployeeUsername(username)) {
            LOGGER.log(Level.INFO, "UsernameAlreadyExistException: Username ''{0}'' is already in use.", username);
            throw new UsernameAlreadyExistException("Username '" + username + "' is already in use.");
        }

        if (isUniqueEmployeeEmail(email)) {
            LOGGER.log(Level.INFO, "EmailAlreadyExistException: Email ''{0}'' is alreagy in use.", email);
            throw new EmailAlreadyExistException("Email '" + email + "' is already in use.");
        }

        if (hasEmployeeGivenRole(email)) {
            LOGGER.log(Level.INFO, "EmployeeAlreadyHasRoleException: Email ''{0}'' is alreagy registered with a given role.", email);
            throw new EmployeeAlreadyHasRoleException("Email '" + email + "' is already in use.");
        }

        Employee newEmployee = new Employee(firstname, lastname, username, email, address, phoneNumber);
        em.persist(newEmployee);
        LOGGER.info("Employee successfully saved...");

        // Insert credentials for employee
        em.createNativeQuery("INSERT INTO usertable (userid, password) VALUES (?, ?)")
                .setParameter(1, email)
                .setParameter(2, password)
                .executeUpdate();

        em.createNativeQuery("INSERT INTO grouptable (userid, groupid) VALUES (?, ?)")
                .setParameter(1, email)
                .setParameter(2, UserGroup.USER_ROLE)
                .executeUpdate();
        LOGGER.info("Employee credentials successfully saved...");
    }

    private boolean isUniqueEmployeeUsername(String username) {
        TypedQuery<Long> query = em.createQuery("SELECT COUNT(e) FROM Employee e WHERE username = :username", Long.class);
        query.setParameter("username", username);
        return query.getSingleResult() != 0;
    }

    private boolean isUniqueEmployeeEmail(String email) {
        TypedQuery<Long> query = em.createQuery("SELECT COUNT(e) FROM Employee e WHERE email = :email", Long.class);
        query.setParameter("email", email);
        return query.getSingleResult() != 0;
    }

    // Check if email already exists in usertable and grouptable tables
    private boolean hasEmployeeGivenRole(String email) {
        Query query = em.createNativeQuery("SELECT usertable.userid FROM usertable INNER JOIN grouptable "
                + "ON usertable.userid=grouptable.userid AND usertable.userid = :email");
        query.setParameter("email", email);
        return !query.getResultList().isEmpty();
    }

    // Hashing password with SHA-256
    public String hashPassword(String password) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(password.getBytes());
        byte byteData[] = md.digest();

        // Convert byte to hex format
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < byteData.length; i++) {
            sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    public List<Employee> findAll() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Employee> cq = cb.createQuery(Employee.class);
        Root<Employee> employee = cq.from(Employee.class);

        // selecting
        cq.select(employee);

        TypedQuery<Employee> tq = em.createQuery(cq);
        return tq.getResultList();
    }

    public int deleteEmployees(List<Employee> employees) {

        int countDeletedRecords = 0;

        Query deleteFromGrouptable = em.createNativeQuery("DELETE FROM grouptable WHERE userid = :userid");
        Query deleteFromUsertable = em.createNativeQuery("DELETE FROM usertable WHERE userid = :userid");

        for (Employee e : employees) {
            // Remove employee
            Employee emp = em.find(Employee.class, e.getId());
            em.remove(emp);
            LOGGER.info("Employee successfully removed..");

            // Delete his credentials also
            // Pass parameters
            deleteFromGrouptable.setParameter("userid", e.getEmail());
            deleteFromUsertable.setParameter("userid", e.getEmail());

            // Execute DELETE statements
            countDeletedRecords += deleteFromGrouptable.executeUpdate();
            countDeletedRecords += deleteFromUsertable.executeUpdate();

            LOGGER.info("Employee credentials successfully removed..");
        }
        return countDeletedRecords;
    }
}
