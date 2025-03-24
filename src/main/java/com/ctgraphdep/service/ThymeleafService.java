package com.ctgraphdep.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;
import org.thymeleaf.spring6.SpringTemplateEngine;

import com.ctgraphdep.utils.LoggerUtil;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.RequestContext;

/**
 * Service for processing Thymeleaf templates programmatically
 */
@Service
public class ThymeleafService {

    private final TemplateEngine templateEngine;

    @Autowired
    public ThymeleafService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;

        // Ensure Spring Security dialect is added if needed
        if (templateEngine instanceof SpringTemplateEngine springTemplateEngine) {
            if (springTemplateEngine.getDialects().stream()
                    .noneMatch(dialect -> dialect instanceof SpringSecurityDialect)) {
                springTemplateEngine.addDialect(new SpringSecurityDialect());
            }
        }

        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Process a Thymeleaf template and return the result as a String
     *
     * @param templateName The template name (path relative to templates directory)
     * @param model The model containing variables to be used in the template
     * @return The processed template as a String
     */
    public String processTemplate(String templateName, Model model) {
        try {
            // Create a Thymeleaf context from the model
            Context context = new Context();

            // Add model attributes to context
            model.asMap().forEach(context::setVariable);

            // Get the current request and response from RequestContextHolder
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                HttpServletResponse response = attributes.getResponse();
                ServletContext servletContext = request.getServletContext();

                // Create Spring RequestContext with both request and response
                RequestContext requestContext = new RequestContext(request, response, servletContext, model.asMap());

                // Add Spring RequestContext to Thymeleaf context
                context.setVariable("springRequestContext", requestContext);

                // Add Spring Security context for sec:authorize tags
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null) {
                    context.setVariable("authentication", authentication);
                }
            }

            // Process the template
            return templateEngine.process(templateName, context);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error processing template " + templateName + ": " + e.getMessage(), e);
            throw new RuntimeException("Error processing template", e);
        }
    }
}