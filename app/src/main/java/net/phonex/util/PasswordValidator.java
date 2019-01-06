package net.phonex.util;

import net.phonex.R;
import net.phonex.util.guava.Lists;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class PasswordValidator {
    private List<Rule> rules;

    private PasswordValidator(){
    }

    public static PasswordValidator build(boolean forceNumber, int minLength){

        List<Rule> rules = new ArrayList<>();
        rules.add(Rule.create("^.{" + minLength +",}$", R.string.pass_validator_short));
        rules.add(Rule.create("[a-zA-Z]", R.string.pass_validator_missing_letter));
        if (forceNumber){
            rules.add(Rule.create("[0-9]", R.string.pass_validator_missing_number));
        }

        PasswordValidator validator = new PasswordValidator();
        validator.rules = rules;
        return validator;
    }


    public List<Rule> validate(final String password){
        List<Rule> violatedRules = Lists.newArrayList();

        for(Rule r : rules ){
            if (!r.pattern.matcher(password).find()){
                violatedRules.add(r);
            }
        }
        return violatedRules;
    }

    public static class Rule{
        private Pattern pattern;
        private int errorStringId;
        public static Rule create(String patternText, int errorStringId){
            Rule r = new Rule();
            r.pattern = Pattern.compile(patternText);
            r.errorStringId = errorStringId;
            return r;
        }

        public Pattern getPattern() {
            return pattern;
        }

        public int getErrorStringId() {
            return errorStringId;
        }
    }
}