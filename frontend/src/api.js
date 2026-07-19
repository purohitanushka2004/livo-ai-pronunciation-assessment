const API_BASE = import.meta.env.VITE_API_BASE_URL || '';

export async function analyzeAudio(file, referenceText) {
  const formData = new FormData();
  formData.append('audio', file);
  if (referenceText) {
    formData.append('referenceText', referenceText);
  }

  const response = await fetch(`${API_BASE}/api/pronunciation/analyze`, {
    method: 'POST',
    body: formData,
  });

  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.error || 'Something went wrong while analyzing your recording.');
  }

  return data;
}
