/*
 * Copyright (c) 2012. The Genome Analysis Centre, Norwich, UK
 * MISO project contacts: Robert Davey, Mario Caccamo @ TGAC
 * *********************************************************************
 *
 * This file is part of MISO.
 *
 * MISO is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MISO is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MISO.  If not, see <http://www.gnu.org/licenses/>.
 *
 * *********************************************************************
 */

function validate_experiment(form) {
  var ok = true;
  var error = "Please correct the following error(s):\n\n";

  if (jQuery('#title').val() == "") {
    ok = false;
    error += "You have not entered a title for the Experiment.\n";
  }

  if (jQuery(':text.validateable').length > 0) {
    jQuery(':text.validateable').each(function() {
      var result = Utils.validation.validate_input_field(this, 'Experiment', ok);
      ok = result.okstatus;
      error += result.errormsg;
    })
  }

  if (!ok) {
    alert(error);
  }

  return ok;
}
