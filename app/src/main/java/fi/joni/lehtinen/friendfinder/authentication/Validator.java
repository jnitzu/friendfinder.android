package fi.joni.lehtinen.friendfinder.authentication;

/**
 * Created by Joni Lehtinen on 6.7.2016.
 */
public class Validator {

    public boolean isEmailValid(String email) {
        return email.contains("@");
    }

    public boolean isPasswordValid(String password) {
        return true;/*
        boolean correctLength = password.length() >= 8;
        boolean hasUppercase = !password.equals(password.toLowerCase());
        boolean hasLowercase = !password.equals(password.toUpperCase());
        boolean hasSpecial   = !password.matches("[A-Za-z0-9 ]*");

        return password.length() > 4;**/
    }

    public boolean isCircleNameValid(String name){
        return true;
    }
}
