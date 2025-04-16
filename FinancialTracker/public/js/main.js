// Main JavaScript for Bank SMS Tracker

document.addEventListener('DOMContentLoaded', function() {
  // Enable Bootstrap tooltips
  const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
  tooltipTriggerList.map(function (tooltipTriggerEl) {
    return new bootstrap.Tooltip(tooltipTriggerEl);
  });
  
  // Initialize month select fields
  const monthSelect = document.getElementById('monthSelect');
  if (monthSelect) {
    monthSelect.addEventListener('change', function() {
      // Extract month and year from the combined value
      const [year, month] = this.value.split('-');
      
      // Update form action with appropriate query parameters
      const form = this.closest('form');
      if (form) {
        form.action = form.action.split('?')[0] + `?month=${month}&year=${year}`;
        form.submit();
      }
    });
  }
});