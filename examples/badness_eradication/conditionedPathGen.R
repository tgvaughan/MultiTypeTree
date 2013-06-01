# Conditioned CTMC path generation

drawPath <- function (startType, m, L) {

  times <- c()
  types <- c()
  
  t <- 0
  d <- startType
  while (TRUE) {

    t <- t + rexp(1,-Q[d,d])
    
    destTypes <- which(Q[d,]>=0)
    d <- sample(destTypes, 1, prob=Q[d,destTypes])

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

generateEnsemble <- function (N, startType, endType, m, L) {

  totalCounts <- c()
  counts <- list()
  
  for (i in 1:N) {
      path <- drawConditionedPath(startType, endType, m, L)
      totalCounts <- append(totalCounts, length(path$times))
      if (i == 1) {
          for (c in 1:dim(m)[1])
              counts[[c]] <- sum(path$types==c)
      } else {
          for (c in 1:dim(m)[1])
              counts[[c]] <- append(counts[[c]], sum(path$types==c))
      }
  }

  res <- list()
  res$totalCounts <- totalCounts
  res$counts <- counts
  
  return (res)
  
}

# Four-colour path generation
Q = matrix(data=c(-0.210000, 0.010000, 0.020000, 0.030000, 0.040000, -0.200000, 0.050000, 0.060000, 0.070000, 0.080000, -0.190000, 0.090000, 0.100000, 0.110000, 0.120000, -0.180000), nrow=4, ncol=4, byrow=T)

ensemble <- generateEnsemble(10000, 1, 2, Q, 200)
