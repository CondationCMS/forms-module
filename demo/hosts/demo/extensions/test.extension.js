import { $hooks } from 'system/hooks.mjs';

$hooks.registerAction("forms/test-form/submit", (context) => {
	console.log("test-form submitted", context);
	return null;
})