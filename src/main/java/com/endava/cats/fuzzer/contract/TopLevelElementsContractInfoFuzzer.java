package com.endava.cats.fuzzer.contract;

import com.endava.cats.fuzzer.ContractInfoFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.google.common.collect.Sets;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@ContractInfoFuzzer
@Component
@Slf4j
public class TopLevelElementsContractInfoFuzzer extends BaseContractInfoFuzzer {

    @Autowired
    public TopLevelElementsContractInfoFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        log.info("Analyzing contract top elements...");
        testCaseListener.addScenario(log, "Scenario: Check if the OpenAPI contract defines elements such as tags, info, external docs and servers");
        testCaseListener.addExpectedResult(log, "Elements should be present and provide meaningful information");
        testCaseListener.addPath("NA");
        StringBuilder errorString = new StringBuilder();

        Set<String> missingFieldsSet = this.checkInfo(data.getOpenApi().getInfo());


        errorString.append(this.checkElement("servers", this.inspectServers(data.getOpenApi().getServers())));

        if (CollectionUtils.isEmpty(data.getOpenApi().getTags())) {
            missingFieldsSet.add("tags");
        } else {
            errorString.append(this.checkElement("tags", this.inspectTags(data.getOpenApi().getTags())));
        }

        if (!missingFieldsSet.isEmpty()) {
            errorString.append(String.format("The following elements are missing: %s. <br/>", this.bold(missingFieldsSet.toString())));
        }

        if (errorString.toString().isEmpty()) {
            testCaseListener.reportInfo(log, "OpenAPI contract contains all top level relevant information!");
        } else {
            testCaseListener.reportError(log, errorString.toString());
        }
    }

    private String checkElement(String element, String errors) {
        if (!errors.isEmpty()) {
            return String.format("%s is misconfigured:%s %s", this.bold(element), errors, this.newLine(2));
        }

        return EMPTY;
    }

    public String inspectServers(List<Server> servers) {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < servers.size(); i++) {
            Server server = servers.get(i);
            String serverString = this.emptyOrShort(server::getDescription, DESCRIPTION);

            if (!serverString.isEmpty()) {
                builder.append(" server[").append(i).append("]= ").append(StringUtils.stripStart(serverString, ","));
            }
        }
        return builder.toString();
    }

    public String inspectTags(List<Tag> tags) {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < tags.size(); i++) {
            String tagString = "";
            Tag tag = tags.get(i);
            if (StringUtils.isBlank(tag.getName())) {
                tagString += "name" + IS_EMPTY;
            }
            tagString += this.emptyOrShort(tag::getDescription, DESCRIPTION);

            if (!tagString.isEmpty()) {
                builder.append("tag[").append(i).append("] = ").append(StringUtils.stripStart(tagString.trim(), ","));
            }
        }
        return builder.toString();
    }

    private String emptyOrShort(Supplier<String> supplier, String field) {
        String result = EMPTY;
        if (StringUtils.isBlank(supplier.get())) {
            result += COMMA + field + IS_EMPTY;
        } else if (supplier.get().split(" ").length <= 3) {
            result += COMMA + field + IS_TOO_SHORT;
        }

        return result;
    }

    private Set<String> checkInfo(Info info) {
        if (info == null) {
            return Sets.newHashSet("info.title", "info.description", "info.version", "info.contact.name",
                    "info.contact.email", "info.contact.url");
        }

        Set<String> missingFields = new HashSet<>();
        missingFields.add(this.getOrEmpty(info::getTitle, "title"));
        missingFields.add(this.getOrEmpty(info::getDescription, DESCRIPTION));
        missingFields.add(this.getOrEmpty(info::getVersion, "version"));

        missingFields.addAll(checkContact(info.getContact()));

        return missingFields.stream().filter(field -> !field.isEmpty())
                .map(field -> "info." + field).collect(Collectors.toSet());
    }

    public Set<String> checkContact(Contact contact) {
        if (contact == null) {
            return Sets.newHashSet("contact.email", "contact.name", "contact.url");
        }

        Set<String> missingFields = new HashSet<>();
        missingFields.add(this.getOrEmpty(contact::getEmail, "email"));
        missingFields.add(this.getOrEmpty(contact::getName, "name"));
        missingFields.add(this.getOrEmpty(contact::getUrl, "url"));

        return missingFields.stream().filter(field -> !field.isEmpty())
                .map(field -> "contact." + field).collect(Collectors.toSet());
    }

    @Override
    protected String runKey(FuzzingData data) {
        return "1";
    }

    @Override
    public String description() {
        return "verifies that all OpenAPI contract level elements are present and provide meaningful information: API description, documentation, title, version, etc. ";
    }
}
