package com.endava.cats.fuzzer.fields.leading;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.EmojiFuzzer;
import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.fuzzer.fields.base.InvisibleCharsBaseTrimValidateFuzzer;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@FieldFuzzer
@EmojiFuzzer
@ConditionalOnExpression(value = "'${edgeSpacesStrategy:trimAndValidate}'=='trimAndValidate' and ${fuzzer.fields.LeadingSingleCodePointEmojisInFields.enabled}")
public class LeadingSingleCodePointEmojisInFieldsTrimValidateFuzzer extends InvisibleCharsBaseTrimValidateFuzzer {

    @Autowired
    protected LeadingSingleCodePointEmojisInFieldsTrimValidateFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
        super(sc, lr, cu, cp);
    }

    @Override
    protected String typeOfDataSentToTheService() {
        return "values prefixed with single code points emojis";
    }

    @Override
    public List<String> getInvisibleChars() {
        return catsUtil.getSingleCodePointEmojis();
    }

    @Override
    public FuzzingStrategy concreteFuzzStrategy() {
        return FuzzingStrategy.prefix();
    }
}