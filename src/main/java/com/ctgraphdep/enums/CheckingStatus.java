package com.ctgraphdep.enums;

public enum CheckingStatus {
    CHECKING_INPUT,    //-user input - new for checking
    TL_CHECK_DONE,     //-team lead check before admin new for checking approves the check register
    TL_EDITED,         //-similar with tl_check_done but overwrites checking_input and tl_check_done
    TL_BLANK,         //new for checking
    ADMIN_DONE         //-final sync
}
