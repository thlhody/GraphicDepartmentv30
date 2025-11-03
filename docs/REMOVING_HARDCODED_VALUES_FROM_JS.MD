> no refactoring just answear a question and tell me in js can i make a js file that contains hardcoded values like in java and can use those hardcoded values or declare them at the begining and when i change those values they will be
used everywhere?

● Yes! JavaScript can do exactly that. You have a few options:

Option 1: ES6 Modules (Modern, Recommended)

Create constants.js:
// constants.js
export const AT_IMPOSTARE = 'IMPOSTARE';
export const AT_SPIZED = 'SPIZED';
export const REGULAR_NAME = 'regular';
export const SPIZED_NAME = 'spized';
export const MONTH_NAMES_SHORT = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

Use in other files:
// register.js
import { AT_IMPOSTARE, AT_SPIZED, MONTH_NAMES_SHORT } from './constants.js';

if (entry.actionType === AT_IMPOSTARE) {
// ...
}

Option 2: Global Object (Works in Thymeleaf templates)

In your HTML or included first:
const AppConstants = {
AT_IMPOSTARE: 'IMPOSTARE',
AT_SPIZED: 'SPIZED',
REGULAR_NAME: 'regular',
SPIZED_NAME: 'spized',
MONTH_NAMES_SHORT: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
};

Use anywhere after it's loaded:
if (entry.actionType === AppConstants.AT_IMPOSTARE) {
// ...
}

Option 3: At the beginning of each file

const AT_IMPOSTARE = 'IMPOSTARE';
const AT_SPIZED = 'SPIZED';
// ... rest of your code

Best choice for your Spring Boot app: Option 2 (Global Object) is easiest since you're using Thymeleaf. Just create static/js/constants.js and include it before your other scripts in the layout.

> so in other words create a constants js and make it load when the website its opened the default page and then anybody can access it?

● Exactly! Here's how:

Step-by-step:

1. Create src/main/resources/static/js/constants.js:
   const AppConstants = {
   AT_IMPOSTARE: 'IMPOSTARE',
   AT_SPIZED: 'SPIZED',
   REGULAR_NAME: 'regular',
   SPIZED_NAME: 'spized',
   // ... all your constants
   };

2. Include it in src/main/resources/templates/layout/main.html (in the <head> or before other scripts):
  <script th:src="@{/js/legacy/constants.js}"></script>
  <!-- Then your other scripts -->
  <script th:src="@{/js/legacy/default.js}"></script>

3. Use anywhere in any other JS file:
   // register.js
   if (actionType === AppConstants.IMPOSTARE) {
   // works!
   }

// statistics.js  
console.log(AppConstants.MONTH_NAMES_SHORT[0]); // "Jan"

Because constants.js loads first in your main layout, the AppConstants object is globally available on every page. Change the value once in constants.js, and it updates everywhere automatically.

That's the simplest approach for your Thymeleaf-based app!
