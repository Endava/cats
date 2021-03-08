package com.endava.cats.args;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Component
@Getter
public class AuthArguments {
    private final List<CatsArg> args = new ArrayList<>();

    @Value("${sslKeystore:empty}")
    private String sslKeystore;
    @Value("${sslKeystorePwd:empty}")
    private String sslKeystorePwd;
    @Value("${sslKeyPwd:empty}")
    private String sslKeyPwd;
    @Value("${basicauth:empty}")
    private String basicAuth;

    @Value("${arg.auth.sslKeystore.help:help}")
    private String sslKeystoreHelp;
    @Value("${arg.auth.sslKeystorePwd.help:help}")
    private String sslKeystorePwdHelp;
    @Value("${arg.auth.sslKeyPwd.help:help}")
    private String sslKeyPwdHelp;
    @Value("${arg.auth.basicAuth.help:help}")
    private String basicAuthHelp;

    @PostConstruct
    public void init() {
        args.add(CatsArg.builder().name("sslKeystore").value(sslKeystore).help(sslKeystoreHelp).build());
        args.add(CatsArg.builder().name("sslKeystorePwd").value(sslKeystorePwd).help(sslKeystorePwdHelp).build());
        args.add(CatsArg.builder().name("sslKeyPwd").value(sslKeyPwd).help(sslKeyPwdHelp).build());
        args.add(CatsArg.builder().name("basicauth").value(basicAuth).help(basicAuthHelp).build());
    }

}
