
window.ActionTypeConstansts = {
    IMPOSTARE: 'IMPOSTARE',
    AT_SPIZED: 'SPIZED',
    REGULAR_NAME: 'regular',
    SPIZED_NAME: 'spized',

};




const AppConstants = {

    // ... all your constants
};

// Constants for complexity calculations
const ACTION_TYPE_VALUES = {
    'ORDIN': 2.5,
    'REORDIN': 1.0,
    'CAMPION': 2.5,
    'PROBA STAMPA': 2.5,
    'ORDIN SPIZED': 2.0,
    'CAMPION SPIZED': 2.0,
    'PROBA S SPIZED': 2.0,
    'PROBA CULOARE': 2.5,
    'CARTELA CULORI': 2.5,
    'CHECKING': 3.0,
    'DESIGN': 2.5,
    'DESIGN 3D': 3.0,
    'PATTERN PREP': 2.5,
    'IMPOSTARE': 0.0,
    'OTHER': 2.5
};

// Print prep types that add complexity
const COMPLEXITY_PRINT_PREPS = {
    'SBS': 0.5,
    'NN': 0.5,
    'NAME': 0.5,
    'NUMBER': 0.5,
    'FLEX': 0.5,
    'BRODERIE': 0.5,
    'OTHER': 0.5
};

// Print prep types that don't affect complexity
const NEUTRAL_PRINT_PREPS = {
    'DIGITAL': 0.0,
    'GPT': 0.0,
    'LAYOUT':0.0,
    'FILM': 0.0
};

const CHECK_TYPE_VALUES = {
    'LAYOUT': 1.0,
    'KIPSTA LAYOUT': 0.25,
    'LAYOUT CHANGES': 0.25,
    'GPT': 0.1,          // For articles; also 0.1 for pieces
    'PRODUCTION': 0.1,
    'REORDER': 0.1,
    'SAMPLE': 0.3,
    'OMS PRODUCTION': 0.1,
    'KIPSTA PRODUCTION': 0.1
};

// Types that use articlesNumbers for calculation
const ARTICLE_BASED_TYPES = [
    'LAYOUT',
    'KIPSTA LAYOUT',
    'LAYOUT CHANGES',
    'GPT'
];

// Types that use filesNumbers for calculation
const FILE_BASED_TYPES = [
    'PRODUCTION',
    'REORDER',
    'SAMPLE',
    'OMS PRODUCTION',
    'KIPSTA PRODUCTION',
    'GPT'  // GPT uses both articles and files
];

const daysOfWeek = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

// Handle time off types (expanded with CR, CN, D, CE)
if (['CO', 'CM', 'SN', 'W', 'CR', 'CN', 'D', 'CE'].includes(value.toUpperCase())) {
    return true; // Valid time off types
}

if (!['SN', 'CO', 'CM', 'W', 'CE'].includes(type)) {
    alert('Invalid type. Use SN, CO, CM, W, or CE (e.g., SN:7.5, CE:6)');
    return false;
}

const typeLabels = {
    'SN': 'National Holiday',
    'CO': 'Time Off',
    'CM': 'Medical Leave',
    'W': 'Weekend'
};

// Try to parse different formats
if (cellContent === 'SN') {
    entryData.timeOffType = 'SN';
    entryData.timeOffLabel = 'National Holiday';
} else if (cellContent === 'CO') {
    entryData.timeOffType = 'CO';
    entryData.timeOffLabel = 'Vacation';
} else if (cellContent === 'CM') {
    entryData.timeOffType = 'CM';
    entryData.timeOffLabel = 'Medical Leave';
} else if (cellContent === 'CR') {
    entryData.timeOffType = 'CR';
    entryData.timeOffLabel = 'Recovery Leave (CR)';
} else if (cellContent === 'CN') {
    entryData.timeOffType = 'CN';
    entryData.timeOffLabel = 'Unpaid Leave (CN)';
} else if (cellContent === 'D') {
    entryData.timeOffType = 'D';
    entryData.timeOffLabel = 'Delegation (D)';
} else if (cellContent === 'CE') {
    entryData.timeOffType = 'CE';
    entryData.timeOffLabel = 'Event Leave (CE)';
} else if (cellContent.startsWith('ZS-')) {
    // Handle ZS-5 format
    entryData.timeOffType = cellContent;
    entryData.timeOffLabel = getTimeOffLabel(cellContent);
} else if (cellContent.startsWith('SN') && cellContent.length > 2) {
    // Handle SN4 format
    const hours = cellContent.substring(2);
    entryData.timeOffType = 'SN';
    entryData.timeOffLabel = 'National Holiday';
    entryData.overtimeHours = hours + 'h';
} else if (cellContent.startsWith('CO') && cellContent.length > 2 && /^\d+$/.test(cellContent.substring(2))) {
    // Handle CO6 format
    const hours = cellContent.substring(2);
    entryData.timeOffType = 'CO';
    entryData.timeOffLabel = 'Vacation';
    entryData.overtimeHours = hours + 'h';
} else if (cellContent.startsWith('CM') && cellContent.length > 2 && /^\d+$/.test(cellContent.substring(2))) {
    // Handle CM4 format
    const hours = cellContent.substring(2);
    entryData.timeOffType = 'CM';
    entryData.timeOffLabel = 'Medical Leave';
    entryData.overtimeHours = hours + 'h';
} else if (cellContent.startsWith('CE') && cellContent.length > 2 && /^\d+$/.test(cellContent.substring(2))) {
    // Handle CE6 format
    const hours = cellContent.substring(2);
    entryData.timeOffType = 'CE';
    entryData.timeOffLabel = 'Event Leave (CE)';
    entryData.overtimeHours = hours + 'h';
} else if (cellContent.includes('h') || /^\d+$/.test(cellContent)) {
    // Regular work hours
    entryData.workHours = cellContent.includes('h') ? cellContent : cellContent + 'h';
}

return entryData;


function getTimeOffLabel(timeOffType) {
    if (!timeOffType) return timeOffType;

    // Handle ZS format (ZS-5 means missing 5 hours)
    if (timeOffType.startsWith('ZS-')) {
        const missingHours = timeOffType.split('-')[1];
        return `Short Day (missing ${missingHours}h)`;
    }

    switch (timeOffType.toUpperCase()) {
        case 'SN': return 'National Holiday';
        case 'CO': return 'Vacation';
        case 'CM': return 'Medical Leave';
        case 'W': return 'Weekend Work';
        case 'CR': return 'Recovery Leave (CR)';
        case 'CN': return 'Unpaid Leave (CN)';
        case 'D': return 'Delegation (D)';
        case 'CE': return 'Event Leave (CE)';
        default: return timeOffType;
    }
}

function getTimeOffIcon(timeOffType) {
    if (!timeOffType) return 'bi bi-calendar-x';

    // Handle ZS format
    if (timeOffType.startsWith('ZS-')) {
        return 'bi bi-hourglass-split text-warning';
    }

    switch (timeOffType.toUpperCase()) {
        case 'SN': return 'bi bi-calendar-event text-success';
        case 'CO': return 'bi bi-airplane text-info';
        case 'CM': return 'bi bi-heart-pulse text-warning';
        case 'W': return 'bi bi-calendar-week text-secondary';
        case 'CR': return 'bi bi-battery-charging text-success';
        case 'CN': return 'bi bi-dash-circle text-secondary';
        case 'D': return 'bi bi-briefcase text-primary';
        case 'CE': return 'bi bi-gift text-danger';
        default: return 'bi bi-calendar-x';
    }
}

function getTimeOffDescription(timeOffType) {
    if (!timeOffType) return '';

    // Handle ZS format
    if (timeOffType.startsWith('ZS-')) {
        const missingHours = timeOffType.split('-')[1];
        return `User worked less than schedule. Missing ${missingHours} hours will be deducted from overtime.`;
    }

    switch (timeOffType.toUpperCase()) {
        case 'CR':
            return 'Recovery Leave - Paid day off using overtime balance. Deducts full schedule hours (8h) from overtime → regular time.';
        case 'CN':
            return 'Unpaid Leave - Day off without payment. Does not count as work day or deduct from balances.';
        case 'D':
            return 'Delegation / Business Trip - Normal work day with special documentation. Counts as regular work day.';
        case 'CE':
            return 'Event Leave - Special event (marriage, birth, death). Free days per company policy. Field 2 required in form.';
        case 'SN':
            return 'National Holiday - Company holiday. If worked, all time counts as overtime.';
        case 'CO':
            return 'Vacation - Paid time off using vacation balance. Deducts from annual vacation days.';
        case 'CM':
            return 'Medical Leave - Sick day. Does not deduct from vacation balance.';
        case 'W':
            return 'Weekend Work - Work on weekend day. All time counts as overtime.';
        default:
            return '';
    }
}

function getStatusLabel(adminSync) {
    switch (adminSync) {
        case 'USER_DONE': return 'User Completed';
        case 'ADMIN_EDITED': return 'Admin Modified';
        case 'USER_IN_PROCESS': return 'In Progress';
        case 'ADMIN_BLANK': return 'Admin Blank';
        default: return adminSync;
    }
}

function getStatusClass(adminSync) {
    switch (adminSync) {
        case 'USER_DONE': return 'text-success';
        case 'ADMIN_EDITED': return 'text-warning';
        case 'USER_IN_PROCESS': return 'text-info';
        case 'ADMIN_BLANK': return 'text-secondary';
        default: return 'text-muted';
    }
}

const months = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December'
];