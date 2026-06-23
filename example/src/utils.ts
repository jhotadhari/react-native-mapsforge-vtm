export const randomNumber = (min: number, max: number): number =>
	Math.random() * (max - min) + min;
