# SmartInbox.Emby.Server

Deep Learning server that works as recommender system for [Emby](https://emby.media/). 

## Motivations

[How to avoid copying movies that you will never play?](http://likewastoldtome.blogspot.com/2019/12/how-to-avoid-copying-movies-that-you.html)

It turns out that I have a compulsion and obsession to watch movies. It is better to say, to copy and organize movies on my personal storage. But some of those movies will be never played.

Recently, I also noticed that I am running out of space. A well known approach to solve this situation could be to eliminate all those movies that I never played or all that I really don't like.

[Emby](https://emby.media/) also track for me all the movies that I already played and that is a perfect ground truth to be used to solve a classification problem:

 _I want to predict if I will play a movie from the following features: Official Rating, Community Rating, Critic Rating and Genres in correlation with my own playback action._
