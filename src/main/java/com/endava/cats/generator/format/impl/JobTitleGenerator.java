package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Generates random job titles.
 */
@Singleton
public class JobTitleGenerator implements ValidDataFormatGenerator, OpenAPIFormat {
    static final List<String> JOB_TITLES = List.of(
            "Software Engineer",
            "Data Scientist",
            "Product Manager",
            "UX Designer",
            "Quality Assurance Analyst",
            "Systems Administrator",
            "Network Engineer",
            "Business Analyst",
            "Technical Writer",
            "Project Manager",
            "Database Administrator",
            "DevOps Engineer",
            "Registered Nurse",
            "Mechanical Engineer",
            "Financial Analyst",
            "Teacher",
            "Graphic Designer",
            "Marketing Manager",
            "Civil Engineer",
            "Chef",
            "Sales Representative",
            "Human Resources Specialist",
            "Electrician",
            "Pharmacist",
            "Architect",
            "Journalist",
            "Accountant",
            "Lawyer",
            "Environmental Scientist",
            "Physician",
            "Social Worker",
            "Police Officer",
            "Pilot",
            "Veterinarian",
            "Data Analyst",
            "Automotive Technician",
            "Dentist",
            "Construction Manager",
            "Musician",
            "Event Planner",
            "Fitness Trainer",
            "Security Analyst",
            "Mobile App Developer",
            "Cloud Architect",
            "IT Support Specialist",
            "Frontend Developer",
            "Backend Developer",
            "Full Stack Developer",
            "Machine Learning Engineer",
            "Blockchain Developer",
            "Artificial Intelligence Specialist",
            "Cybersecurity Specialist",
            "Digital Marketing Manager",
            "SEO Specialist",
            "Content Strategist",
            "Game Developer",
            "Data Engineer",
            "Site Reliability Engineer",
            "Hardware Engineer"
    );
    private static final String JOB_TITLE_WORD = "jobtitle";

    @Override
    public Object generate(Schema<?> schema) {
        return CatsUtil.selectRandom(JOB_TITLES);
    }

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return JOB_TITLE_WORD.equalsIgnoreCase(PropertySanitizer.sanitize(format)) ||
                PropertySanitizer.sanitize(propertyName).equalsIgnoreCase(JOB_TITLE_WORD);
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("jobTitle", "job-title", "job_title");
    }
}