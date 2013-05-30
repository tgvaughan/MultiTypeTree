# Conditioned CTMC path generation

drawPath <- function (startType, m, L) {

  times <- c()
  types <- c()
  
  t <- 0
  d <- startType
  while (TRUE) {
    
    t <- t + rexp(1,m[d+1])
    d <- 1 - d

    if (t>L) {
      break
    }

    times <- append(times,t)
    types <- append(types,d)
  }

  res <- list()
  res$times <- times
  res$types <- types

  return (res)
  
}

drawConditionedPath <- function (startType, endType, m, L) {

  while (TRUE) {
    res <- drawPath(startType, m, L)
    if (length(res$types)>0) {
      if (res$types[length(res$types)]==endType)
        return (res)
    } else {
      if (startType == endType)
        return (res)
    }
  }
  
}

generateCountEnsemble <- function (N, startType, endType, m, L) {

  counts <- c()
  
  for (i in 1:N)
    counts <- append(counts, length(drawConditionedPath(startType, endType, m, L)$times))

  return (counts)
  
}
