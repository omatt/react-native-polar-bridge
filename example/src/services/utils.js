export function convertSecondsToHMSS(seconds) {
    const hours = Math.floor(seconds / 3600); // 1 hour = 3600 seconds
    const minutes = Math.floor((seconds % 3600) / 60); // Get remaining minutes
    const remainingSeconds = seconds % 60; // Get remaining seconds

    // Format as string with pluralization and omission of 0 values
    let timeString = '';

    if (hours > 0) {
        timeString += `${hours} hour${hours > 1 ? 's' : ''}`;
    }
    if (minutes > 0) {
        if (timeString) {timeString += ' ';}
        timeString += `${minutes} minute${minutes > 1 ? 's' : ''}`;
    }
    if (remainingSeconds > 0) {
        if (timeString) {timeString += ' ';}
        timeString += `${remainingSeconds} second${remainingSeconds > 1 ? 's' : ''}`;
    }

    return timeString || '0 seconds'; // Return '0 seconds' if all values are 0
}

export const formatDateYYYYMMDD = (unixTimestamp) => {
    const date = new Date(unixTimestamp * 1000); // Convert from seconds to ms
    return date.toISOString().split('T')[0]; // Get 'yyyy-mm-dd'
};

export const formatDateYYYYMMDDHHMMSS = (unixTimestamp) => {
    const date = new Date(unixTimestamp); // Convert from seconds to milliseconds

    const pad = (num) => String(num).padStart(2, '0');

    const year = date.getFullYear();
    const month = pad(date.getMonth() + 1); // Months are 0-indexed
    const day = pad(date.getDate());
    const hours = pad(date.getHours());
    const minutes = pad(date.getMinutes());
    const seconds = pad(date.getSeconds());

    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
};

// Extract the device's timezone using `Intl.DateTimeFormat`
export const timezone = new Intl.DateTimeFormat('en-US', { timeZoneName: 'short' }).format(new Date()).split(' ').pop();
