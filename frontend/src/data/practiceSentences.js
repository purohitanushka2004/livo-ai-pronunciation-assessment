// Short passages sized to read naturally in ~30-45 seconds at a
// conversational pace (roughly 90-110 words). Used for the "read aloud"
// mode, where we can compare exactly what was said against this target
// text instead of only trusting recognizer confidence.
export const PRACTICE_SENTENCES = [
  {
    id: 'weather',
    text: "The weather this morning was cold and clear, with a light breeze coming in from the coast. By afternoon the sun had broken through the clouds, and people started walking along the beach again. Some children were building sandcastles near the water while their parents watched from a distance. A few boats were anchored just offshore, rocking gently with the waves. It felt like the perfect kind of day to be outside, away from screens and deadlines, just enjoying the salty air and the sound of the tide.",
  },
  {
    id: 'coffee',
    text: "Every morning before work, she walks to the small coffee shop on the corner of her street. The owner always remembers her order and greets her with a warm smile. She likes to sit by the window, watching people rush past on their way to the office. Sometimes she brings a book, but most days she just enjoys the quiet moment before her day gets busy. That short walk and the smell of fresh coffee help her feel ready for whatever comes next.",
  },
  {
    id: 'travel',
    text: "Learning a new language becomes so much easier when you actually travel to a place where people speak it every day. At first, ordering food or asking for directions can feel intimidating, but most locals are patient and happy to help. Making small mistakes is part of the process, and nobody expects you to be perfect right away. Over time, listening carefully and practicing out loud, even if you feel a little embarrassed, builds real confidence in speaking naturally.",
  },
  {
    id: 'technology',
    text: "Technology has changed the way we communicate with friends and family who live far away. A video call today can feel almost as natural as sitting in the same room together. Still, many people say that nothing quite replaces an actual visit or a handwritten letter. There is something personal about spending real time with someone, without any screen in between. Perhaps the best approach is to enjoy the convenience of technology while still making room for those simple, human moments.",
  },
  {
    id: 'exercise',
    text: "Doctors often recommend at least thirty minutes of exercise most days of the week to stay healthy. This does not have to mean running long distances or lifting heavy weights at a gym. A brisk walk, a bicycle ride, or even dancing in your living room can make a real difference over time. The key is finding an activity you actually enjoy, so that staying active feels less like a chore and more like a natural part of your daily routine.",
  },
  {
    id: 'weekend',
    text: "On Saturday morning, the market in the town square fills up with fresh fruit, vegetables, and homemade bread. Vendors call out to passersby, offering samples of cheese and fresh juice. Families wander between the stalls, filling their baskets while children chase pigeons near the fountain. By midday, the smell of grilled food drifts through the crowd, and most people stop for a quick bite before heading home. It is one of the liveliest mornings of the week in the whole neighborhood.",
  },
];

export function randomPracticeSentence(excludeId) {
  const candidates = excludeId
    ? PRACTICE_SENTENCES.filter((s) => s.id !== excludeId)
    : PRACTICE_SENTENCES;
  return candidates[Math.floor(Math.random() * candidates.length)];
}
