/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.password;

import static org.jboss.as.domain.management.logging.DomainManagementLogger.ROOT_LOGGER;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import org.jboss.as.domain.management.security.password.PasswordCheckResult.Result;
import org.jboss.as.domain.management.security.password.simple.SimplePasswordStrengthChecker;

/**
 * Simple util which narrows down password checks so there is no hassle in performing those checks in CLI.
 *
 * @author baranowb
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class PasswordCheckUtil {

    public static final String _PROPERTY_CHECKER = "password.restriction.checker";
    public static final String _PROPERTY_STRENGTH = "password.restriction.strength";
    public static final String _PROPERTY_FORBIDDEN = "password.restriction.forbiddenValue";

    public static final String _PROPERTY_RESTRICTION = "password.restriction";
    public static final String _PROPERTY_MIN_LENGTH = "password.restriction.minLength";
    public static final String _PROPERTY_MIN_ALPHA = "password.restriction.minAlpha";
    public static final String _PROPERTY_MIN_DIGIT = "password.restriction.minDigit";
    public static final String _PROPERTY_MIN_SYMBOL = "password.restriction.minSymbol";
    public static final String _PROPERTY_MATCH_USERNAME = "password.restriction.mustNotMatchUsername";

    private PasswordStrengthChecker passwordStrengthChecker;
    private PasswordStrength acceptable = PasswordStrength.MODERATE;
    private RestrictionLevel level = RestrictionLevel.WARN;
    // Something ordered is good so the ordering of messages and validation is consistent across invocations.
    public List<PasswordRestriction> passwordValuesRestrictions = new ArrayList<PasswordRestriction>();
    private CompoundRestriction compountRestriction = null;

    private PasswordCheckUtil(final File configFile, final RestrictionLevel level) {
        this.level = level;
        if (configFile != null && configFile.exists()) {
            try {
                Properties configProperties = new Properties();
                configProperties.load(new FileInputStream(configFile));
                // level
                initRestrictionLevel(configProperties);
                // strength
                initDefaultStrength(configProperties);
                // match username
                initMustNotMatchUsername(configProperties);
                // checker
                initStrengthChecker(configProperties);
                // name restrictions
                initPasswordRestrictions(configProperties);
                // length
                initMinLength(configProperties);
                // alpha
                initMinAlpha(configProperties);
                // digit
                initMinDigit(configProperties);
                // symbol
                initMinSymbol(configProperties);
            } catch (IOException e) {
                simple();
            }
        } else {
            simple();
        }
    }

    private void simple() {
        // revert to simple
        this.passwordStrengthChecker = new SimplePasswordStrengthChecker();
    }

    public static PasswordCheckUtil create(final File configFile) {
        return new PasswordCheckUtil(configFile, null);
    }

    public static PasswordCheckUtil create(RestrictionLevel level) {
        return new PasswordCheckUtil(null, level);
    }

    private boolean must() {
        return RestrictionLevel.REJECT == level;
    }

    /**
     * @param props
     */
    private void initPasswordRestrictions(Properties props) {
        try {
            String forbiddens = props.getProperty(_PROPERTY_FORBIDDEN);
            if (forbiddens == null) {
                return;
            }

            String[] values = forbiddens.split(",");
            this.passwordValuesRestrictions.add(new ValueRestriction(values, must()));
        } catch (Exception e) {
            // log?
        }
    }

    /**
     * @param props
     */
    private void initStrengthChecker(Properties props) {
        try {
            String stringClassName = props.getProperty(_PROPERTY_CHECKER);
            if (stringClassName == null) {
                this.simple();
                return;
            }

            Class<PasswordStrengthChecker> clazz = (Class<PasswordStrengthChecker>) PasswordCheckUtil.class
                    .forName(stringClassName);
            this.passwordStrengthChecker = clazz.newInstance();
        } catch (Exception e) {
            this.simple();
        }
    }

    /**
     * @param props
     */
    private void initDefaultStrength(Properties props) {
        try {
            this.acceptable = PasswordStrength.valueOf(props.getProperty(_PROPERTY_STRENGTH).toUpperCase(Locale.ENGLISH));
        } catch (Exception e) {
            // log
        }
    }

    /**
     * @param props
     */
    private void initMinAlpha(Properties props) {
        try {
            int minAlpha = Integer.parseInt(props.getProperty(_PROPERTY_MIN_ALPHA));
            createAlphaRestriction(minAlpha);
        } catch (Exception e) {
            // log
        }
    }

    /**
     * @param props
     */
    private void initMinSymbol(Properties props) {
        try {
            int minAlpha = Integer.parseInt(props.getProperty(_PROPERTY_MIN_SYMBOL));
            createSymbolRestriction(minAlpha);
        } catch (Exception e) {
            // log
        }
    }

    /**
     * @param props
     */
    private void initMinDigit(Properties props) {
        try {
            int minDigit = Integer.parseInt(props.getProperty(_PROPERTY_MIN_DIGIT));
            createDigitRestriction(minDigit);
        } catch (Exception e) {
            // log
        }
    }

    /**
     * @param props
     */
    private void initMinLength(Properties props) {
        try {
            int minLength = Integer.parseInt(props.getProperty(_PROPERTY_MIN_LENGTH));
            createLengthRestriction(minLength);
        } catch (Exception e) {
            // log
        }
    }

    /**
     * @param props
     */
    private void initMustNotMatchUsername(Properties props) {
        try {
            if (Boolean.parseBoolean(props.getProperty(_PROPERTY_MATCH_USERNAME))) {
                passwordValuesRestrictions.add(new UsernamePasswordMatch(must()));
            }
        } catch (Exception e) {
            // log
        }
    }

    /**
     * @param props
     */
    private void initRestrictionLevel(Properties props) {
        try {
            level = RestrictionLevel.valueOf(props.getProperty(_PROPERTY_RESTRICTION));
        } catch (Exception e) {
            // log
        }
    }

    private boolean assertStrength(PasswordStrength result) {
        return result.getStrength() >= this.acceptable.getStrength();
    }

    /**
     * Method which performs strength checks on password. It returns outcome which can be used by CLI.
     *
     * @param isAdminitrative - administrative checks are less restrictive. This means that weak password or one which violates restrictions is not indicated as failure.
     * Administrative checks are usually performed by admin changing/setting default password for user.
     * @param userName - the name of user for which password is set.
     * @param password - password.
     * @return
     */
    public PasswordCheckResult check(boolean isAdminitrative, String userName, String password) {
        // TODO: allow custom restrictions?
        List<PasswordRestriction> passwordValuesRestrictions = getPasswordRestrictions();
        final PasswordStrengthCheckResult strengthResult = this.passwordStrengthChecker.check(userName, password, passwordValuesRestrictions);

        final int failedRestrictions = strengthResult.getRestrictionFailures().size();
        final PasswordStrength strength = strengthResult.getStrength();
        final boolean strongEnough = assertStrength(strength);

        PasswordCheckResult.Result resultAction;
        String resultMessage = null;
        if (isAdminitrative) {
            if (strongEnough) {
                if (failedRestrictions > 0) {
                    resultAction = Result.WARN;
                    resultMessage = strengthResult.getRestrictionFailures().get(0).getMessage();
                } else {
                    resultAction = Result.ACCEPT;
                }
            } else {
                resultAction = Result.WARN;
                resultMessage = ROOT_LOGGER.passwordNotStrongEnough(strength.toString(), this.acceptable.toString());
            }
        } else {
            if (strongEnough) {
                if (failedRestrictions > 0) {
                    resultAction = Result.REJECT;
                    resultMessage = strengthResult.getRestrictionFailures().get(0).getMessage();
                } else {
                    resultAction = Result.ACCEPT;
                }
            } else {
                if (failedRestrictions > 0) {
                    resultAction = Result.REJECT;
                    resultMessage = strengthResult.getRestrictionFailures().get(0).getMessage();
                } else {
                    resultAction = Result.REJECT;
                    resultMessage = ROOT_LOGGER.passwordNotStrongEnough(strength.toString(), this.acceptable.toString());
                }
            }
        }

        return new PasswordCheckResult(resultAction, resultMessage);

    }

    public RestrictionLevel getRestrictionLevel() {
        return level;
    }

    public List<PasswordRestriction> getPasswordRestrictions() {
        return Collections.unmodifiableList(passwordValuesRestrictions);
    }

    private void addToCompointRestriction(final PasswordRestriction toWrap) {
        if (compountRestriction == null) {
            compountRestriction = new CompoundRestriction(level == RestrictionLevel.REJECT);
            passwordValuesRestrictions.add(compountRestriction);
        }
        compountRestriction.add(toWrap);
    }

    public void createLengthRestriction(int minLength) {
        if (minLength > 0) {
            addToCompointRestriction(new LengthRestriction(minLength, must()));
        }
    }

    public PasswordRestriction createAlphaRestriction(int minAlpha) {
        return createRegExRestriction(minAlpha, SimplePasswordStrengthChecker.REGEX_ALPHA,
                ROOT_LOGGER.passwordMustHaveAlphaInfo(minAlpha), must() ? ROOT_LOGGER.passwordMustHaveAlpha(minAlpha)
                        : ROOT_LOGGER.passwordShouldHaveAlpha(minAlpha));
    }

    public PasswordRestriction createDigitRestriction(int minDigit) {
        return createRegExRestriction(minDigit, SimplePasswordStrengthChecker.REGEX_DIGITS,
                ROOT_LOGGER.passwordMustHaveDigitInfo(minDigit), must() ? ROOT_LOGGER.passwordMustHaveDigit(minDigit)
                        : ROOT_LOGGER.passwordShouldHaveDigit(minDigit));
    }

    public PasswordRestriction createSymbolRestriction(int minSymbol) {
        return createRegExRestriction(minSymbol, SimplePasswordStrengthChecker.REGEX_SYMBOLS,
                ROOT_LOGGER.passwordMustHaveSymbolInfo(minSymbol), must() ? ROOT_LOGGER.passwordMustHaveSymbol(minSymbol)
                        : ROOT_LOGGER.passwordShouldHaveSymbol(minSymbol));
    }

    private PasswordRestriction createRegExRestriction(int minChar, String regex, String requirementsMessage,
            String failureMessage) {
        if (minChar > 0) {
            PasswordRestriction pr = new RegexRestriction(String.format("(.*%s.*){%d}", regex, minChar), requirementsMessage,
                    failureMessage);
            addToCompointRestriction(pr);
            return pr;
        }
        return null;
    }
}
