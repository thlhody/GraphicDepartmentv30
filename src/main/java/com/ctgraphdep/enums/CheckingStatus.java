package com.ctgraphdep.enums;

public enum CheckingStatus {
    CHECKING_INPUT,    //-user input
    CHECKING_DONE,     //-final sync
    TL_CHECK_DONE,     //-team lead check before admin
    TL_EDITED,         //-similar with tl_check_done but overwrites checking_input and checking_done
    ADMIN_EDITED,      //-overwrites everything
    ADMIN_BLANK,       //-trigger remove in all the other statuses
    ADMIN_DONE         //-final sync
}
